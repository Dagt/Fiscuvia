package com.fiscuvia;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Drives the real OIDC redirects/forms and crypto verification over HTTPS. No security test authentication filter. */
final class OidcBrowser implements AutoCloseable {
    final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL) {
        @Override public Map<String,java.util.List<String>> get(URI uri,Map<String,java.util.List<String>> headers) throws java.io.IOException {
            // Chromium treats localhost as trustworthy even over HTTP. The local IdP uses secure cookies;
            // Java's CookieManager does not implement that browser exception. Confine the fixture to this IdP.
            if ("localhost".equals(uri.getHost()) && uri.getPort()==idp.getMappedPort(8080))
                uri=URI.create(uri.toString().replaceFirst("^http:","https:"));
            return super.get(uri,headers);
        }
    };
    final HttpClient client;
    final String origin;
    final KeycloakTestProvider idp;
    String username;
    static final JsonMapper JSON=JsonMapper.builder().build();
    OidcBrowser(LocalTls tls,KeycloakTestProvider idp) {
        client=HttpClient.newBuilder().cookieHandler(cookies).sslContext(tls.context()).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        origin=tls.origin(); this.idp=idp;
    }
    HttpResponse<String> get(String path) throws Exception { return send("GET",path,null,null); }
    HttpResponse<String> send(String method,String path,String body,String csrf) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(path.startsWith("http")?path:origin+path)).timeout(Duration.ofSeconds(15));
        if(csrf!=null) builder.header("X-CSRF-TOKEN",csrf);
        if(body!=null) builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String csrf() throws Exception { var response=get("/api/v1/auth/csrf"); assertThat(response.statusCode()).isEqualTo(200); return json(response).path("token").asText(); }
    URI authorize() throws Exception {
        var begin=send("POST","/api/v1/auth/login",null,csrf()); assertThat(begin.statusCode()).isEqualTo(303);
        var redirect=get(begin.headers().firstValue("Location").orElseThrow()); assertThat(redirect.statusCode()).isEqualTo(302);
        URI uri=URI.create(redirect.headers().firstValue("Location").orElseThrow());
        assertThat(uri.getQuery()).contains("code_challenge=","code_challenge_method=S256","state=","nonce=");
        return uri;
    }
    HttpResponse<String> credentials(URI authorization,String username,String password) throws Exception {
        var form=get(authorization.toString()); assertThat(form.statusCode()).isEqualTo(200);
        return form(form,Map.of("username",username,"password",password,"credentialId",""));
    }
    HttpResponse<String> form(HttpResponse<String> page,Map<String,String> fields) throws Exception {
        var action=Pattern.compile("<form[^>]*action=\"([^\"]+)\"",Pattern.CASE_INSENSITIVE).matcher(page.body());
        assertThat(action.find()).as("real Keycloak form").isTrue();
        String url=action.group(1).replace("&amp;","&");
        String body=fields.entrySet().stream().map(entry->encode(entry.getKey())+"="+encode(entry.getValue())).collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    URI callback(String username) throws Exception {
        return callback(username,0);
    }
    URI callback(String username,int otpOffset) throws Exception {
        if(this.username==null) this.username=idp.nextUser(username);
        var password=credentials(authorize(),this.username,idp.password);
        assertThat(password.statusCode()).isEqualTo(200);
        assertThat(password.body()).contains("name=\"otp\"");
        var otp=form(password,Map.of("otp",idp.otp(otpOffset),"login","Iniciar sesión"));
        assertThat(otp.statusCode()).isEqualTo(302);
        URI callback=URI.create(otp.headers().firstValue("Location").orElseThrow());
        assertThat(callback.getPath()).as("Keycloak completed real password and OTP with no pending required action").isEqualTo("/api/v1/auth/callback/local");
        return callback;
    }
    JsonNode login(String username) throws Exception {
        var response=get(callback(username).toString()); assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).contains(origin+"/app");
        var me=get("/api/v1/me"); assertThat(me.statusCode()).isEqualTo(200); return json(me);
    }
    static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    String sessionCookie() { return cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("__Host-FiscuviaSession")).findFirst().orElseThrow().getValue(); }
    @Override public void close() { client.close(); }
}
