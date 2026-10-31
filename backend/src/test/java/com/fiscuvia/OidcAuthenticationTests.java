package com.fiscuvia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @Timeout(60)
class OidcAuthenticationTests {
    @Autowired SessionRepository<? extends Session> sessions;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoderFactory<org.springframework.security.oauth2.client.registration.ClientRegistration> decoders;
    @Autowired org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clients;
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DATABASE=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        DATABASE.register(registry); TLS.register(registry);
        registry.add("fiscuvia.identity.enabled",()->true); registry.add("fiscuvia.identity.issuer",OIDC::issuer);
        registry.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);
        registry.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");
    }
    @Test void realAuthorizationCodePkceOtpLoginPersistsOnlyMinimalSession() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            String before=browser.csrf(); String old=browser.sessionCookie();
            var me=browser.login("carol");
            assertThat(me.path("memberships").size()).isZero();
            assertThat(me.path("privilegedMfaVerified").asBoolean()).isTrue();
            assertThat(browser.sessionCookie()).isNotEqualTo(old);
            assertThat(browser.csrf()).isNotEqualTo(before);
            assertThat(sessions.findById(sessionId(old))).isNull();
            Session session=sessions.findById(sessionId(browser.sessionCookie()));
            assertThat(session).isNotNull();
            assertThat(session.getAttributeNames()).doesNotContain("SPRING_SECURITY_SAVED_REQUEST","SPRING_SECURITY_LAST_EXCEPTION",
                    "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST");
            org.springframework.security.core.context.SecurityContext context=session.getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(context.getAuthentication().getPrincipal()).isNotInstanceOf(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
            assertThat(context.getAuthentication().getDetails()).isNull();
            try(var connection=migration();var query=connection.createStatement();var rows=query.executeQuery("SELECT attribute_bytes FROM auth_sessions.spring_session_attributes")) {
                while(rows.next()) assertThat(new String(rows.getBytes(1),java.nio.charset.StandardCharsets.ISO_8859_1))
                        .doesNotContain("OidcIdToken","OAuth2AccessToken","OAuth2RefreshToken","OAuth2AuthorizedClient","DefaultOidcUser",OIDC.password,OIDC.clientSecret,DATABASE.actorKey);
            }
            var response=browser.get("/api/v1/me");
            assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
            assertThat(response.body()).doesNotContain("issuer","subject","access_token","id_token","password","alice");
        }
    }
    @Test void anonymousRequestsAndArbitraryIdentityHeadersCannotAuthenticate() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            assertThat(browser.get("/api/v1/auth/authorize/local").statusCode()).isEqualTo(401);
            var response=browser.client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(TLS.origin()+"/api/v1/me?userId="+UUID.randomUUID()))
                    .header("X-User-Id",UUID.randomUUID().toString()).header("X-Tenant-Id",UUID.randomUUID().toString())
                    .header("Authorization","Bearer synthetic-private-token").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(OidcBrowser.json(response).path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
            assertThat(response.body()).doesNotContain("synthetic-private-token","userId","SQLException");
        }
    }
    @Test void preAuthenticationProtocolSessionContainsNoProviderCredentialsOrOAuthTokens() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            browser.authorize();
            try(var connection=migration();var query=connection.prepareStatement("SELECT a.attribute_bytes FROM auth_sessions.spring_session_attributes a JOIN auth_sessions.spring_session s ON s.primary_id=a.session_primary_id WHERE s.session_id=?")) {
                query.setString(1,sessionId(browser.sessionCookie())); int attributes=0;
                try(var rows=query.executeQuery()) {
                    while(rows.next()) { attributes++; assertThat(new String(rows.getBytes(1),java.nio.charset.StandardCharsets.ISO_8859_1))
                            .doesNotContain(OIDC.clientSecret,OIDC.password,DATABASE.actorKey,"OAuth2AccessToken","OAuth2RefreshToken","OidcIdToken","DefaultOidcUser"); }
                }
                assertThat(attributes).isGreaterThan(0);
            }
        }
    }
    @Test void loginAndLogoutRequireCsrfAndCookiesAreSecure() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            var csrf=browser.get("/api/v1/auth/csrf");
            String cookie=csrf.headers().allValues("Set-Cookie").stream().filter(value->value.startsWith("__Host-FiscuviaSession=")).findFirst().orElseThrow();
            assertThat(cookie).contains("Secure","HttpOnly","SameSite=Lax","Path=/").doesNotContain("Domain=");
            assertThat(browser.send("POST","/api/v1/auth/login",null,null).statusCode()).isEqualTo(403);
            assertThat(browser.send("POST","/api/v1/auth/login",null,"not-a-csrf-token").statusCode()).isEqualTo(403);
            browser.login("alice"); String session=sessionId(browser.sessionCookie());
            assertThat(browser.send("POST","/api/v1/auth/logout",null,null).statusCode()).isEqualTo(403);
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(200);
            assertThat(browser.send("POST","/api/v1/auth/logout",null,browser.csrf()).statusCode()).isEqualTo(204);
            assertThat(sessions.findById(session)).isNull();
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void wrongCredentialsAndOtpNeverCreateAnApplicationIdentity() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            String username=OIDC.nextUser("alice");
            var failed=browser.credentials(browser.authorize(),username,"synthetic-wrong-password");
            assertThat(failed.statusCode()).isEqualTo(200); assertThat(failed.body()).contains("name=\"password\"");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            String username=OIDC.nextUser("bob");
            var otp=browser.credentials(browser.authorize(),username,OIDC.password);
            var failed=browser.form(otp,Map.of("otp","invalid-otp","login","Iniciar sesión"));
            assertThat(failed.statusCode()).isEqualTo(200); assertThat(failed.body()).contains("name=\"otp\"");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
            try(var connection=migration();var select=connection.prepareStatement("SELECT count(*) FROM identity_private.external_identities WHERE issuer=? AND subject=?")) {
                select.setString(1,OIDC.issuer()); select.setString(2,KeycloakTestProvider.subject(username).toString());
                try(var rows=select.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
            }
        }
    }
    @Test void stateManipulationIsRejectedAndProtocolSessionIsDestroyed() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            var callback=browser.callback("alice"); String id=sessionId(browser.sessionCookie());
            var failed=browser.get(callback.toString().replaceFirst("state=[^&]+","state=untrusted-state"));
            assertThat(failed.statusCode()).isEqualTo(302); assertThat(failed.headers().firstValue("Location")).contains(TLS.origin()+"/login?error=autenticacion");
            assertThat(sessions.findById(id)).isNull(); assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void nonceManipulationIsRejectedAfterRealTokenExchange() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            var authorization=browser.authorize();
            browser.username=OIDC.nextUser("alice");
            var password=browser.credentials(java.net.URI.create(authorization.toString().replaceFirst("nonce=[^&]+","nonce=untrusted-nonce")),browser.username,OIDC.password);
            var callback=browser.form(password,Map.of("otp",OIDC.otp()));
            var failed=browser.get(callback.headers().firstValue("Location").orElseThrow());
            assertThat(failed.headers().firstValue("Location")).contains(TLS.origin()+"/login?error=autenticacion");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void pkceChallengeManipulationIsRejectedByRealKeycloak() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            var authorization=browser.authorize(); browser.username=OIDC.nextUser("alice");
            var password=browser.credentials(java.net.URI.create(authorization.toString().replaceFirst("code_challenge=[^&]+","code_challenge=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")),browser.username,OIDC.password);
            var callback=browser.form(password,Map.of("otp",OIDC.otp()));
            var failed=browser.get(callback.headers().firstValue("Location").orElseThrow());
            assertThat(failed.headers().firstValue("Location")).contains(TLS.origin()+"/login?error=autenticacion");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void idleAndAbsoluteExpirationInvalidateSessions() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            browser.login("alice");
            editSession(sessions,sessionId(browser.sessionCookie()),session->{ session.setMaxInactiveInterval(java.time.Duration.ofSeconds(1)); session.setLastAccessedTime(Instant.now().minusSeconds(10)); });
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            browser.login("bob"); alterPrincipal(browser,"absoluteExpiry",Instant.now().minusSeconds(1).getEpochSecond());
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void membershipsResolveBeforeTenantContextAndCrossTenantUuidIsDenied() throws Exception {
        try(var alice=new OidcBrowser(TLS,OIDC);var bob=new OidcBrowser(TLS,OIDC)) {
            UUID aliceId=UUID.fromString(alice.login("alice").path("userId").asText());
            UUID bobId=UUID.fromString(bob.login("bob").path("userId").asText());
            UUID a=bootstrap(aliceId), b=bootstrap(bobId), second=bootstrap(aliceId);
            var mine=OidcBrowser.json(alice.get("/api/v1/me"));
            assertThat(mine.path("memberships").size()).isEqualTo(2);
            assertThat(mine.toString()).contains(a.toString(),second.toString()).doesNotContain(b.toString(),bobId.toString());
            assertThat(alice.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+a+"\"}",alice.csrf()).statusCode()).isEqualTo(200);
            assertThat(alice.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+second+"\"}",alice.csrf()).statusCode()).isEqualTo(200);
            assertThat(alice.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+b+"\"}",alice.csrf()).statusCode()).isEqualTo(403);
            assertThat(bob.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+a+"\"}",bob.csrf()).statusCode()).isEqualTo(403);
        }
    }
    @Test void invitationsAreHashedBoundToVerifiedSubjectAndConsumedOnce() throws Exception {
        try(var owner=new OidcBrowser(TLS,OIDC);var recipient=new OidcBrowser(TLS,OIDC);var other=new OidcBrowser(TLS,OIDC)) {
            UUID tenant=bootstrap(UUID.fromString(owner.login("alice").path("userId").asText()));
            recipient.login("bob"); other.login("carol");
            var invitation=invite(owner,recipient,tenant);
            String token=invitation.path("token").asText();
            try(var connection=migration();var select=connection.prepareStatement("SELECT token_hash FROM identity.invitations WHERE tenant_id=? AND id=?")) {
                connection.setAutoCommit(false); try(var establish=connection.prepareStatement("SELECT app.establish_tenant_context(?)")) { establish.setObject(1,tenant); establish.execute(); }
                select.setObject(1,tenant); select.setObject(2,UUID.fromString(invitation.path("invitationId").asText()));
                try(var rows=select.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getBytes(1)).hasSize(32).isNotEqualTo(token.getBytes()); }
                connection.rollback();
            }
            assertThat(accept(other,token)).isEqualTo(403);
            assertThat(accept(recipient,token)).isEqualTo(200);
            assertThat(accept(recipient,token)).isEqualTo(403);
            assertThat(OidcBrowser.json(recipient.get("/api/v1/me")).path("memberships").size()).isEqualTo(1);
        }
    }
    @Test void concurrentInvitationAcceptanceHasExactlyOneWinner() throws Exception {
        try(var owner=new OidcBrowser(TLS,OIDC);var recipient=new OidcBrowser(TLS,OIDC);var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            UUID tenant=bootstrap(UUID.fromString(owner.login("alice").path("userId").asText())); recipient.login("bob");
            String token=invite(owner,recipient,tenant).path("token").asText(), csrf=recipient.csrf();
            var first=executor.submit(()->recipient.send("POST","/api/v1/auth/invitations/accept","{\"token\":\""+token+"\"}",csrf).statusCode());
            var second=executor.submit(()->recipient.send("POST","/api/v1/auth/invitations/accept","{\"token\":\""+token+"\"}",csrf).statusCode());
            assertThat(java.util.List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,403);
        }
    }
    @Test void expiredAndRevokedInvitationsCannotCreateMemberships() throws Exception {
        try(var owner=new OidcBrowser(TLS,OIDC);var recipient=new OidcBrowser(TLS,OIDC)) {
            UUID tenant=bootstrap(UUID.fromString(owner.login("alice").path("userId").asText())); recipient.login("bob");
            var invitation=invite(owner,recipient,tenant);
            try(var connection=migration()) {
                connection.setAutoCommit(false); var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
                sql.update("UPDATE identity.invitations SET expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=?",tenant); connection.commit();
            }
            assertThat(accept(recipient,invitation.path("token").asText())).isEqualTo(403);
            var revoked=invite(owner,recipient,tenant);
            assertThat(owner.send("POST","/api/v1/auth/invitations/revoke","{\"tenantId\":\""+tenant+"\",\"invitationId\":\""+revoked.path("invitationId").asText()+"\"}",owner.csrf()).statusCode()).isEqualTo(204);
            assertThat(accept(recipient,revoked.path("token").asText())).isEqualTo(403);
            assertThat(OidcBrowser.json(recipient.get("/api/v1/me")).path("memberships").size()).isZero();
        }
    }
    @Test void suspensionAndRevocationTakeEffectWithoutTrustingSessionRoles() throws Exception {
        for(String state:java.util.List.of("SUSPENDED","REVOKED")) try(var owner=new OidcBrowser(TLS,OIDC);var recipient=new OidcBrowser(TLS,OIDC)) {
            UUID tenant=bootstrap(UUID.fromString(owner.login("alice").path("userId").asText())); recipient.login("bob");
            assertThat(accept(recipient,invite(owner,recipient,tenant).path("token").asText())).isEqualTo(200);
            String membership=OidcBrowser.json(recipient.get("/api/v1/me")).path("memberships").get(0).path("membershipId").asText();
            assertThat(owner.send("POST","/api/v1/auth/memberships/status","{\"tenantId\":\""+tenant+"\",\"membershipId\":\""+membership+"\",\"status\":\""+state+"\"}",owner.csrf()).statusCode()).isEqualTo(204);
            assertThat(OidcBrowser.json(recipient.get("/api/v1/me")).path("memberships").size()).isZero();
            assertThat(recipient.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+tenant+"\"}",recipient.csrf()).statusCode()).isEqualTo(403);
        }
    }
    @Test void absentAndStaleMfaDenyPrivilegedActionsButNotOwnMembershipListing() throws Exception {
        for(long mfa:java.util.List.of(0L,Instant.now().minusSeconds(601).getEpochSecond())) try(var owner=new OidcBrowser(TLS,OIDC)) {
            UUID tenant=bootstrap(UUID.fromString(owner.login("alice").path("userId").asText())); alterPrincipal(owner,"mfaAt",mfa);
            assertThat(owner.get("/api/v1/me").statusCode()).isEqualTo(200);
            var response=owner.send("POST","/api/v1/auth/invitations","{\"tenantId\":\""+tenant+"\",\"subject\":\"unassigned\",\"role\":\"ACCOUNTANT\"}",owner.csrf());
            assertThat(response.statusCode()).isEqualTo(403);
        }
    }
    @Test void runtimeCannotSpoofActorReadPrivateKeysOrUseBootstrap() throws Exception {
        try(var connection=DriverManager.getConnection(DATABASE.runtimeUrl(),PostgresTestDatabase.RUNTIME_USER,DATABASE.runtimePassword)) {
            var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            assertThatThrownBy(()->sql.queryForObject("SELECT identity.bind_actor(?,?,0,?)",Object.class,OIDC.issuer(),"forged-user","0".repeat(64))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.queryForObject("SELECT secret FROM identity_private.proof_key",byte[].class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.queryForObject("SELECT identity_crypto.hmac('x'::bytea,'y'::bytea,'sha256')",byte[].class)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            sql.queryForObject("SELECT set_config('app.user_id',?,false)",String.class,UUID.randomUUID().toString());
            assertThat(sql.queryForObject("SELECT count(*) FROM identity.memberships",Integer.class)).isZero();
            assertThatThrownBy(()->sql.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void independentSessionsOfSameIdentityHaveSeparateCookiesAndLocalLogout() throws Exception {
        try(var first=new OidcBrowser(TLS,OIDC);var second=new OidcBrowser(TLS,OIDC)) {
            String user=first.login("alice").path("userId").asText();
            second.username=first.username;
            // A different valid native OTP within the realm's normal look-ahead window; never enable reusable OTP.
            var response=second.get(second.callback("alice",1).toString());
            assertThat(response.headers().firstValue("Location")).contains(TLS.origin()+"/app");
            assertThat(OidcBrowser.json(second.get("/api/v1/me")).path("userId").asText()).isEqualTo(user);
            assertThat(second.sessionCookie()).isNotEqualTo(first.sessionCookie());
            assertThat(first.send("POST","/api/v1/auth/logout",null,first.csrf()).statusCode()).isEqualTo(204);
            assertThat(first.get("/api/v1/me").statusCode()).isEqualTo(401);
            assertThat(second.get("/api/v1/me").statusCode()).isEqualTo(200);
        }
    }
    @Test void actualKeycloakJwksRejectInvalidSignatureIssuerAudienceAndExpiredTokens() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            var callback=browser.callback("alice");
            Session session=sessions.findById(sessionId(browser.sessionCookie()));
            String attribute=session.getAttributeNames().stream().filter(name->name.endsWith(".AUTHORIZATION_REQUEST")).findFirst().orElseThrow();
            org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest request=session.getAttribute(attribute);
            Map<String,String> query=java.util.Arrays.stream(callback.getRawQuery().split("&")).map(pair->pair.split("=",2))
                    .collect(java.util.stream.Collectors.toMap(pair->pair[0],pair->java.net.URLDecoder.decode(pair[1],java.nio.charset.StandardCharsets.UTF_8)));
            String body=Map.of("grant_type","authorization_code","client_id","fiscuvia-local","client_secret",OIDC.clientSecret,
                    "redirect_uri",TLS.origin()+"/api/v1/auth/callback/local","code",query.get("code"),"code_verifier",request.<String>getAttribute("code_verifier"))
                    .entrySet().stream().map(entry->OidcBrowser.encode(entry.getKey())+"="+OidcBrowser.encode(entry.getValue())).collect(java.util.stream.Collectors.joining("&"));
            var tokenResponse=browser.client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(OIDC.issuer()+"/protocol/openid-connect/token"))
                    .header("Content-Type","application/x-www-form-urlencoded").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(tokenResponse.statusCode()).isEqualTo(200);
            String token=OidcBrowser.json(tokenResponse).path("id_token").asText();
            var decoder=decoders.createDecoder(clients.findByRegistrationId("local"));
            assertThat(decoder.decode(token).getSubject()).isEqualTo(KeycloakTestProvider.subject(browser.username).toString());
            var original=com.nimbusds.jwt.SignedJWT.parse(token);
            for(var claims:java.util.List.of(
                    new com.nimbusds.jwt.JWTClaimsSet.Builder(original.getJWTClaimsSet()).issuer("https://untrusted.invalid").build(),
                    new com.nimbusds.jwt.JWTClaimsSet.Builder(original.getJWTClaimsSet()).audience("untrusted-client").build(),
                    new com.nimbusds.jwt.JWTClaimsSet.Builder(original.getJWTClaimsSet()).issueTime(java.util.Date.from(Instant.now().minusSeconds(300)))
                        .expirationTime(java.util.Date.from(Instant.now().minusSeconds(120))).build())) {
                var modified=new com.nimbusds.jwt.SignedJWT(original.getHeader(),claims);
                modified.sign(new com.nimbusds.jose.crypto.RSASSASigner(OIDC.signingKey));
                assertThatThrownBy(()->decoder.decode(modified.serialize())).isInstanceOf(org.springframework.security.oauth2.jwt.JwtValidationException.class);
            }
            var badSignature=new com.nimbusds.jwt.SignedJWT(original.getHeader(),original.getJWTClaimsSet());
            badSignature.sign(new com.nimbusds.jose.crypto.RSASSASigner(new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate()));
            assertThatThrownBy(()->decoder.decode(badSignature.serialize())).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        }
    }
    @Test void actualRuntimeRlsSeparatesActorsAndReusedConnectionsWithoutFilters() throws Exception {
        try(var alice=new OidcBrowser(TLS,OIDC);var bob=new OidcBrowser(TLS,OIDC);
                var connection=DriverManager.getConnection(DATABASE.runtimeUrl(),PostgresTestDatabase.RUNTIME_USER,DATABASE.runtimePassword)) {
            UUID a=bootstrap(UUID.fromString(alice.login("alice").path("userId").asText()));
            UUID b=bootstrap(UUID.fromString(bob.login("bob").path("userId").asText()));
            var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            connection.setAutoCommit(false);
            assertThat(sql.queryForObject("SELECT count(*) FROM identity.memberships",Integer.class)).isZero();
            bind(sql,alice.username);
            assertThat(sql.queryForList("SELECT tenant_id FROM identity.memberships",UUID.class)).containsExactly(a);
            assertThat(sql.queryForList("SELECT tenant_id FROM identity.membership_roles",UUID.class)).containsExactly(a);
            sql.queryForObject("SELECT identity.authorize_tenant(?,false)",Object.class,a);
            sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,a);
            assertThat(sql.queryForList("SELECT tenant_id FROM app.tenants",UUID.class)).containsExactly(a);
            assertThatThrownBy(()->sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,b)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            connection.rollback(); bind(sql,alice.username);
            assertThatThrownBy(()->bind(sql,bob.username)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            connection.rollback();
            assertThat(sql.queryForObject("SELECT count(*) FROM identity.memberships",Integer.class)).isZero();
            bind(sql,bob.username);
            sql.queryForObject("SELECT identity.authorize_tenant(?,false)",Object.class,b);
            sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,b);
            assertThat(sql.queryForList("SELECT tenant_id FROM identity.memberships",UUID.class)).containsExactly(b);
            assertThat(sql.queryForList("SELECT tenant_id FROM app.tenants",UUID.class)).containsExactly(b);
            connection.commit();
            assertThat(sql.queryForObject("SELECT count(*) FROM identity.memberships",Integer.class)).isZero(); connection.rollback();
        }
        try(var connection=DriverManager.getConnection(DATABASE.runtimeUrl(),"fiscuvia_sessions",DATABASE.sessionPassword)) {
            assertThatThrownBy(()->connection.createStatement().executeQuery("SELECT * FROM identity.memberships"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
    @Test void bootstrapRequiresVerifiedIdentityApprovalAndCannotBeRepeated() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            UUID user=UUID.fromString(browser.login("alice").path("userId").asText());
            UUID tenant=bootstrap(user);
            try(var connection=migration()) {
                var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                assertThatThrownBy(()->sql.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,tenant,user,UUID.randomUUID()))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThatThrownBy(()->sql.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,UUID.randomUUID(),user,null))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThatThrownBy(()->sql.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
            }
        }
    }
    @Test void invalidMfaAndDisabledGlobalIdentityInvalidateAccess() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            browser.login("alice"); alterPrincipal(browser,"mfaAt",Instant.now().plusSeconds(600).getEpochSecond());
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            UUID user=UUID.fromString(browser.login("bob").path("userId").asText());
            try(var connection=migration();var update=connection.prepareStatement("UPDATE identity_private.users SET status='DISABLED',auth_version=auth_version+1 WHERE id=?")) {
                update.setObject(1,user); update.executeUpdate();
            }
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void authenticatedRequestsAndFailuresDoNotLogCanariesOrProviderSecrets(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)) {
            browser.login("alice"); String canary="DEMO900101XX1-private-auth-canary";
            var response=browser.send("POST","/api/v1/auth/tenant?private="+canary,"{\"tenantId\":\""+canary+"\"}",browser.csrf());
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain(canary,"SQL","stack","issuer","subject");
            assertThat(output.getAll()).doesNotContain(canary,OIDC.password,OIDC.clientSecret,OIDC.otpSecret,DATABASE.actorKey,DATABASE.sessionPassword);
        }
    }
    private void bind(org.springframework.jdbc.core.JdbcTemplate sql,String username) {
        String tx=sql.queryForObject("SELECT pg_current_xact_id()::text",String.class), subject=KeycloakTestProvider.subject(username).toString();
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec(java.util.HexFormat.of().parseHex(DATABASE.actorKey),"HmacSHA256"));
            String proof=java.util.HexFormat.of().formatHex(mac.doFinal((OIDC.issuer()+"\n"+subject+"\n"+tx+"\n0").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            sql.queryForList("SELECT * FROM identity.bind_actor(?,?,0,?)",OIDC.issuer(),subject,proof);
        } catch(java.security.GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    private java.sql.Connection migration() throws Exception { return DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword); }
    private UUID bootstrap(UUID user) throws Exception {
        UUID tenant=UUID.randomUUID();
        try(var connection=migration()) {
            connection.setAutoCommit(false); var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant); sql.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",tenant);
            sql.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,tenant,user,UUID.randomUUID()); connection.commit();
        } return tenant;
    }
    private tools.jackson.databind.JsonNode invite(OidcBrowser owner,OidcBrowser recipient,UUID tenant) throws Exception {
        var response=owner.send("POST","/api/v1/auth/invitations","{\"tenantId\":\""+tenant+"\",\"subject\":\""+KeycloakTestProvider.subject(recipient.username)+"\",\"role\":\"ACCOUNTANT\"}",owner.csrf());
        assertThat(response.statusCode()).isEqualTo(201); return OidcBrowser.json(response);
    }
    private int accept(OidcBrowser browser,String token) throws Exception { return browser.send("POST","/api/v1/auth/invitations/accept","{\"token\":\""+token+"\"}",browser.csrf()).statusCode(); }
    private static String sessionId(String cookie) { return new String(java.util.Base64.getDecoder().decode(cookie.replace("\"","")),java.nio.charset.StandardCharsets.UTF_8); }
    private static <S extends Session> void editSession(SessionRepository<S> repository,String id,java.util.function.Consumer<Session> edit) {
        S session=repository.findById(id); assertThat(session).isNotNull(); edit.accept(session); repository.save(session);
    }
    private void alterPrincipal(OidcBrowser browser,String field,long value) {
        editSession(sessions,sessionId(browser.sessionCookie()),session->{
            org.springframework.security.core.context.SecurityContext old=session.getAttribute("SPRING_SECURITY_CONTEXT");
            Object principal=old.getAuthentication().getPrincipal(); var components=principal.getClass().getRecordComponents();
            try {
                Object[] values=new Object[components.length]; Class<?>[] types=new Class<?>[components.length];
                for(int i=0;i<components.length;i++) { types[i]=components[i].getType(); var accessor=components[i].getAccessor(); accessor.setAccessible(true); values[i]=components[i].getName().equals(field)?value:accessor.invoke(principal); }
                var constructor=principal.getClass().getDeclaredConstructor(types); constructor.setAccessible(true);
                var changed=(org.springframework.security.oauth2.core.user.OAuth2User)constructor.newInstance(values);
                var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(changed,changed.getAuthorities(),"local"));
                session.setAttribute("SPRING_SECURITY_CONTEXT",context);
            } catch(ReflectiveOperationException invalid) { throw new IllegalStateException(invalid); }
        });
    }
}
