package com.fiscuvia;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only upstream corruption point, not an authentication substitute. Every request exchanges
 * a real Keycloak authorization code/PKCE before changing the signed ID token in memory. The BFF
 * still uses the real issuer/JWKS and its production decoder, filters, nonce and session logic. */
final class OidcTokenAttackProxy implements AutoCloseable {
    enum Attack { NONE, SIGNATURE, ISSUER, AUDIENCE, EXPIRED, ROLE_CLAIMS, UNRECOGNIZED_MFA }
    final AtomicReference<Attack> attack=new AtomicReference<>(Attack.NONE);
    private final HttpServer server;
    private final HttpClient upstream=HttpClient.newHttpClient();
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();

    OidcTokenAttackProxy(KeycloakTestProvider provider) {
        try {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(workers);
            server.createContext("/token",exchange->{
                try {
                    if(!exchange.getRequestMethod().equals("POST")){exchange.sendResponseHeaders(405,-1);return;}
                    byte[] request=exchange.getRequestBody().readAllBytes();
                    var forward=HttpRequest.newBuilder(URI.create(provider.issuer()+"/protocol/openid-connect/token"))
                        .header("Content-Type","application/x-www-form-urlencoded");
                    String authorization=exchange.getRequestHeaders().getFirst("Authorization");
                    if(authorization!=null)forward.header("Authorization",authorization);
                    var response=upstream.send(forward.POST(HttpRequest.BodyPublishers.ofByteArray(request)).build(),HttpResponse.BodyHandlers.ofByteArray());
                    byte[] body=response.body();
                    if(response.statusCode()==200 && attack.get()!=Attack.NONE){
                        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
                        var json=(tools.jackson.databind.node.ObjectNode)mapper.readTree(body);
                        var token=com.nimbusds.jwt.SignedJWT.parse(json.path("id_token").asText());
                        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder(token.getJWTClaimsSet());
                        switch(attack.get()){
                            case ISSUER -> claims.issuer("https://untrusted.example.invalid");
                            case AUDIENCE -> claims.audience("untrusted-client");
                            case EXPIRED -> claims.issueTime(java.util.Date.from(Instant.now().minusSeconds(300)))
                                .expirationTime(java.util.Date.from(Instant.now().minusSeconds(120)));
                            case ROLE_CLAIMS -> claims.claim("roles",java.util.List.of("FIRM_OWNER_ADMIN"))
                                .claim("capabilities",java.util.List.of("ALL"));
                            case UNRECOGNIZED_MFA -> claims.claim("acr","unrecognized")
                                .claim("amr",java.util.List.of("pwd","unrecognized-factor"));
                            default -> { }
                        }
                        var changed=new com.nimbusds.jwt.SignedJWT(token.getHeader(),claims.build());
                        var key=attack.get()==Attack.SIGNATURE
                            ?new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate().toRSAPrivateKey():provider.signingKey;
                        changed.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
                        json.put("id_token",changed.serialize());body=mapper.writeValueAsBytes(json);
                    }
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(response.statusCode(),body.length);exchange.getResponseBody().write(body);
                }catch(Exception unavailable){
                    byte[] safe="{\"error\":\"synthetic_upstream_failure\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(502,safe.length);exchange.getResponseBody().write(safe);
                }finally{exchange.close();}
            });
            server.start();
        }catch(java.io.IOException unavailable){throw new IllegalStateException("Local token attack proxy unavailable");}
    }
    String tokenUri(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/token";}
    public void close(){server.stop(0);workers.close();upstream.close();}
}
