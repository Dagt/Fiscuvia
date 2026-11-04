package com.fiscuvia;

import com.fiscuvia.audit.*;
import com.fiscuvia.identity.*;
import com.fiscuvia.platform.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.session.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Cross-stack regression, never an application module or fixture authentication filter. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import({ThreatFixtureController.class,CrossTenantRegressionTests.UpstreamAttackConfiguration.class}) @Timeout(120)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class CrossTenantRegressionTests {
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DATABASE=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    static final OidcTokenAttackProxy TOKENS=new OidcTokenAttackProxy(OIDC);
    static ThreatHarness.Actors actors;static boolean installed;
    static final String ROOT=ThreatFixtureController.ROOT;
    @Autowired DataSource runtime;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired SessionRepository<? extends Session> sessions;
    ThreatHarness f;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){DATABASE.register(r);TLS.register(r);
        r.add("fiscuvia.identity.enabled",()->true);r.add("fiscuvia.identity.issuer",OIDC::issuer);
        r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");
        r.add("spring.datasource.hikari.maximum-pool-size",()->4);}
    @TestConfiguration(proxyBeanMethods=false) static class UpstreamAttackConfiguration {
        @Bean @Primary org.springframework.security.oauth2.client.registration.ClientRegistrationRepository attackClient(
                @Qualifier("oidcClients") org.springframework.security.oauth2.client.registration.ClientRegistrationRepository original){
            var client=org.springframework.security.oauth2.client.registration.ClientRegistration.withClientRegistration(original.findByRegistrationId("local"))
                .tokenUri(TOKENS.tokenUri()).build();
            return new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(client);
        }
    }
    @BeforeEach void fixture() throws Exception {
        TOKENS.attack.set(OidcTokenAttackProxy.Attack.NONE);
        if(!installed){
            try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword);
                    var s=connection.createStatement();var in=getClass().getResourceAsStream("/threat-regression-fixture.sql")){
                s.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            installed=true;actors=new ThreatHarness.Actors(TLS,OIDC);
        }
        actors.ensure();f=new ThreatHarness(DATABASE,actors);
    }
    @AfterEach void resetAttack(){TOKENS.attack.set(OidcTokenAttackProxy.Attack.NONE);}
    @AfterAll static void cleanup(){if(actors!=null)actors.close();TOKENS.close();}

    @Test void validOidcStillRequiresAnActiveMembership() throws Exception {
        assertThat(f.actor("none").browser().get("/api/v1/me").statusCode()).isEqualTo(200);
        assertThat(item("none",f.a,f.a1).statusCode()).isEqualTo(404);
        assertThat(list("none",f.a).statusCode()).isEqualTo(403);
    }
    @Test void invalidSignatureIsRejectedThroughTheRealCallback() throws Exception {badToken(OidcTokenAttackProxy.Attack.SIGNATURE);}
    @Test void wrongIssuerIsRejectedThroughTheRealCallback() throws Exception {badToken(OidcTokenAttackProxy.Attack.ISSUER);}
    @Test void wrongAudienceIsRejectedThroughTheRealCallback() throws Exception {badToken(OidcTokenAttackProxy.Attack.AUDIENCE);}
    @Test void expiredTokenIsRejectedThroughTheRealCallback() throws Exception {badToken(OidcTokenAttackProxy.Attack.EXPIRED);}
    void badToken(OidcTokenAttackProxy.Attack attack) throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)){
            URI callback=browser.callback("alice");TOKENS.attack.set(attack);
            var response=browser.get(callback.toString());
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("Location")).contains(TLS.origin()+"/login?error=autenticacion");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
            assertThat(f.rows(null,"SELECT user_id FROM identity_private.external_identities WHERE issuer=? AND subject=?",OIDC.issuer(),KeycloakTestProvider.subject(browser.username).toString())).isEmpty();
        }finally{TOKENS.attack.set(OidcTokenAttackProxy.Attack.NONE);}
    }
    @Test void signedRoleClaimsCannotCreateAdministrativeAuthority() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)){
            TOKENS.attack.set(OidcTokenAttackProxy.Attack.ROLE_CLAIMS);
            UUID user=UUID.fromString(browser.login("bob").path("userId").asText());f.member(f.a,user,"ACCOUNTANT");
            assertThat(browser.send("POST","/api/v1/auth/invitations",inviteBody(f.a),browser.csrf()).statusCode()).isEqualTo(403);
        }finally{TOKENS.attack.set(OidcTokenAttackProxy.Attack.NONE);}
    }
    @Test void signedUnrecognizedMfaCannotAuthorizeOwnerCommands() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)){
            TOKENS.attack.set(OidcTokenAttackProxy.Attack.UNRECOGNIZED_MFA);
            var me=browser.login("carol");assertThat(me.path("privilegedMfaVerified").asBoolean()).isFalse();
            UUID tenant=UUID.randomUUID();f.admin(tenant,s->{s.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",tenant);
                s.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,tenant,UUID.fromString(me.path("userId").asText()),UUID.randomUUID());});
            assertThat(browser.send("POST","/api/v1/auth/invitations",inviteBody(tenant),browser.csrf()).statusCode()).isEqualTo(403);
        }finally{TOKENS.attack.set(OidcTokenAttackProxy.Attack.NONE);}
    }
    @Test void forgedSessionCookieCannotReuseAnotherIdentity() throws Exception {
        try(var browser=new OidcBrowser(TLS,OIDC)){
            String forged=Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var cookie=new HttpCookie("__Host-FiscuviaSession",forged);cookie.setPath("/");cookie.setSecure(true);
            browser.cookies.getCookieStore().add(URI.create(TLS.origin()),cookie);
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
            assertThat(browser.get(ROOT+f.a+"/item/"+f.a1).statusCode()).isEqualTo(401);
            assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(200);
        }
    }
    @Test void simultaneousSessionsAcrossTenantsAreRevokedByGlobalAuthVersion() throws Exception {
        try(var first=new OidcBrowser(TLS,OIDC);var second=new OidcBrowser(TLS,OIDC);var workers=Executors.newVirtualThreadPerTaskExecutor()){
            UUID user=UUID.fromString(first.login("carol").path("userId").asText());
            second.username=first.username;assertThat(second.get(second.callback("carol",1).toString()).headers().firstValue("Location")).contains(TLS.origin()+"/app");
            UUID ma=f.member(f.a,user,"ACCOUNTANT"),mb=f.member(f.b,user,"ACCOUNTANT");f.grant(f.a,ma,f.a1,"TAXPAYER_VIEW");f.grant(f.b,mb,f.b1,"TAXPAYER_VIEW");
            var barrier=new CyclicBarrier(2);
            var a=workers.submit(()->{barrier.await();return first.get(ROOT+f.a+"/list");});
            var b=workers.submit(()->{barrier.await();return second.get(ROOT+f.b+"/list");});
            assertThat(a.get().body()).contains(f.a1.toString()).doesNotContain(f.b1.toString());
            assertThat(b.get().body()).contains(f.b1.toString()).doesNotContain(f.a1.toString());
            assertThat(first.sessionCookie()).isNotEqualTo(second.sessionCookie());
            f.admin(null,s->s.update("UPDATE identity_private.users SET auth_version=auth_version+1 WHERE id=?",user));
            assertThat(first.get(ROOT+f.a+"/list").statusCode()).isEqualTo(401);
            assertThat(second.get(ROOT+f.b+"/list").statusCode()).isEqualTo(401);
        }
    }
    @Test void disabledGlobalUserCannotKeepPreviouslyAuthorizedPortfolio() throws Exception {
        assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(200);
        f.admin(null,s->s.update("UPDATE identity_private.users SET status='DISABLED' WHERE id=?",f.actor("a").user()));
        assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(401);
        assertThat(batch("a",f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(401);
        assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.successes(f.a)).isZero();
    }
    @Test void sameSyntheticRfcAcrossThreeDispatchesNeverSharesRecords() throws Exception {
        assertThat(f.rows(f.a,"SELECT synthetic_rfc FROM threat_fixture.resources WHERE id=?",f.a1).getFirst().get("synthetic_rfc")).isEqualTo(f.demoRfc);
        assertThat(f.rows(f.b,"SELECT synthetic_rfc FROM threat_fixture.resources WHERE id=?",f.b1).getFirst().get("synthetic_rfc")).isEqualTo(f.demoRfc);
        assertPortfolio("a",f.a,f.a1);assertPortfolio("b",f.b,f.b1);
        assertThat(item("a",f.b,f.b1).statusCode()).isEqualTo(404);assertThat(item("a",f.c,f.c1).statusCode()).isEqualTo(404);
        assertThat(batch("a",f.b,UUID.randomUUID(),"normal",f.b1).statusCode()).isEqualTo(403);
        assertThat(f.version(f.b,f.b1)).isZero();
    }
    @Test void sameTenantPortfoliosAndRoleHierarchyRemainDistinct() throws Exception {
        assertPortfolio("a",f.a,f.a1);assertPortfolio("peer",f.a,f.a2);
        assertThat(item("peer",f.a,f.a1).statusCode()).isEqualTo(404);
        assertThat(OidcBrowser.json(list("supervisor",f.a)).path("items").size()).isZero();
        assertThat(list("owner",f.a).statusCode()).isEqualTo(403);assertThat(list("assistant",f.a).statusCode()).isEqualTo(403);
        assertThat(f.actor("assistant").browser().send("POST","/api/v1/auth/invitations",inviteBody(f.a),f.actor("assistant").browser().csrf()).statusCode()).isEqualTo(403);
    }
    @Test void validUuidAndSpoofedHeadersBodyOrQueryDoNotAuthorize() throws Exception {
        var request=HttpRequest.newBuilder(URI.create(TLS.origin()+ROOT+f.a+"/item/"+f.a2+"?tenantId="+f.b+"&role=FIRM_OWNER_ADMIN&userId="+f.actor("peer").user()))
            .header("X-Role","FIRM_OWNER_ADMIN").header("X-Tenant-Id",f.b.toString()).header("X-User-Id",f.actor("peer").user().toString()).GET().build();
        var denied=f.actor("a").browser().client.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(404);assertThat(denied.body()).doesNotContain(f.a2.toString(),f.b.toString(),f.actor("peer").user().toString());
        var badBody=f.actor("a").browser().send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+f.b+"\",\"userId\":\""+f.actor("b").user()+"\",\"role\":\"FIRM_OWNER_ADMIN\"}",f.actor("a").browser().csrf());
        assertThat(badBody.statusCode()).isEqualTo(400);assertThat(item("a",f.b,f.b1).statusCode()).isEqualTo(404);
    }
    @Test void missingUnassignedAndForeignResourcesHaveEquivalentSafeErrors() throws Exception {
        var results=List.of(item("a",f.a,f.a2),item("a",f.a,UUID.randomUUID()),item("a",f.b,f.b1));
        String detail=OidcBrowser.json(results.getFirst()).path("detail").asText();
        for(var response:results){assertThat(response.statusCode()).isEqualTo(404);assertThat(OidcBrowser.json(response).path("detail").asText()).isEqualTo(detail);
            assertThat(response.body()).doesNotContain(f.demoRfc,f.a2.toString(),f.b1.toString(),"SQLException");}
    }
    @Test void paginationAndCountsAreRestrictedBeforeReturningRows() throws Exception {
        var page=OidcBrowser.json(f.actor("a").browser().get(ROOT+f.a+"/list?page=0&size=1"));
        assertThat(page.path("count").asInt()).isEqualTo(1);assertThat(page.path("items").toString()).contains(f.a1.toString()).doesNotContain(f.a2.toString(),f.b1.toString());
        assertThat(OidcBrowser.json(f.actor("a").browser().get(ROOT+f.a+"/list?page=1&size=1")).path("items").size()).isZero();
    }
    @Test void readDoesNotGrantEditAndEditDoesNotGrantExport() throws Exception {
        assertThat(batch("peer",f.a,UUID.randomUUID(),"normal",f.a2).statusCode()).isEqualTo(404);
        assertThat(batch("a",f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(200);
        assertThat(f.actor("a").browser().get(ROOT+f.a+"/export").body()).isEqualTo("[]");
        assertThat(f.version(f.a,f.a2)).isZero();assertThat(f.version(f.a,f.a1)).isEqualTo(1);
    }
    @Test void inactiveMembershipsAndIdentityWithoutMembershipFailClosed() throws Exception {
        for(String who:List.of("suspended","revoked","none")){
            assertThat(list(who,f.a).statusCode()).isEqualTo(403);
            assertThat(batch(who,f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(403);
        }
        assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.successes(f.a)).isZero();
    }
    @Test void successfulCommandAttributesActorAndCorrelationAndReplaysOnce() throws Exception {
        UUID command=UUID.randomUUID();var response=batch("a",f.a,command,"normal",f.a1);
        assertThat(response.statusCode()).isEqualTo(200);var replay=batch("a",f.a,command,"normal",f.a1);
        assertThat(OidcBrowser.json(replay).path("replay").asBoolean()).isTrue();assertThat(f.version(f.a,f.a1)).isEqualTo(1);
        var event=f.rows(f.a,"SELECT * FROM journal.events WHERE command_id=?",command).getFirst();
        assertThat(event.get("actor_id")).isEqualTo(f.actor("a").user());assertThat(event.get("tenant_id")).isEqualTo(f.a);
        assertThat(event.get("correlation_id").toString()).isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow());
        assertThat(f.rows(f.a,"SELECT * FROM journal.outbox WHERE event_id=?",event.get("event_id"))).hasSize(1);
        assertThat(batch("multi",f.a,command,"normal",f.a1).statusCode()).isEqualTo(409);
        assertThat(f.version(f.a,f.a1)).isEqualTo(1);
    }
    @Test void concurrentReplayHasOneEffectOneAuditAndOneOutbox() throws Exception {
        UUID command=UUID.randomUUID();String csrf=f.actor("a").browser().csrf();var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            Callable<HttpResponse<String>> run=()->{barrier.await();return f.actor("a").browser().send("POST",ROOT+f.a+"/batch/"+command,body(f.a1),csrf);};
            var first=workers.submit(run);var second=workers.submit(run);
            assertThat(first.get().statusCode()).isEqualTo(200);assertThat(second.get().statusCode()).isEqualTo(200);
        }
        assertThat(f.version(f.a,f.a1)).isEqualTo(1);assertThat(f.successes(f.a)).isEqualTo(1);
    }
    @Test void partialBatchAndPostJournalFailureRollBackRowsEventsAndOutbox() throws Exception {
        for(String mode:List.of("normal","rollback","after-journal")){
            UUID command=UUID.randomUUID();var response=mode.equals("normal")?batch("a",f.a,command,mode,f.a1,f.a2):batch("a",f.a,command,mode,f.a1);
            assertThat(response.statusCode()).isEqualTo(mode.equals("normal")?404:409);
            assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.version(f.a,f.a2)).isZero();
            assertThat(f.rows(f.a,"SELECT * FROM journal.events WHERE command_id=?",command)).isEmpty();
            assertThat(f.successes(f.a)).isZero();
        }
    }
    @Test void journalOrOutboxInsertFailurePreventsSensitiveCommit() throws Exception {
        for(String table:List.of("journal.events","journal.outbox")){
            f.admin(null,s->s.execute("CREATE TRIGGER threat_failure BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION threat_fixture.fail_write()"));
            try{assertThat(batch("a",f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(500);}
            finally{f.admin(null,s->s.execute("DROP TRIGGER threat_failure ON "+table));}
            assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.successes(f.a)).isZero();
        }
    }
    @Test void csrfFailureCannotWriteRowsOrSuccessJournal() throws Exception {
        assertThat(f.actor("a").browser().send("POST",ROOT+f.a+"/batch/"+UUID.randomUUID(),body(f.a1),"forged-csrf").statusCode()).isEqualTo(403);
        assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.successes(f.a)).isZero();
    }
    @Test void committedMembershipRevocationDeniesTheNextAuditedCommand() throws Exception {
        f.admin(f.a,s->s.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",f.members.get("a")));
        assertThat(batch("a",f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(403);
        assertThat(f.version(f.a,f.a1)).isZero();assertThat(f.successes(f.a)).isZero();
    }
    @Test void revocationDuringAuditedCommandWaitsAndNextOperationDenies() throws Exception {
        ThreatFixtureController.entered=new CountDownLatch(1);ThreatFixtureController.release=new CountDownLatch(1);
        String csrf=f.actor("a").browser().csrf();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var command=workers.submit(()->f.actor("a").browser().send("POST",ROOT+f.a+"/batch/"+UUID.randomUUID()+"?mode=hold",body(f.a1),csrf));
            try{
                assertThat(ThreatFixtureController.entered.await(10,TimeUnit.SECONDS)).isTrue();
                var revoke=workers.submit(()->{f.admin(f.a,s->s.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",f.members.get("a")));return true;});
                ThreatHarness.until(()->f.blocked(PostgresTestDatabase.MIGRATION_USER));assertThat(revoke.isDone()).isFalse();
                ThreatFixtureController.release.countDown();assertThat(command.get().statusCode()).isEqualTo(200);assertThat(revoke.get()).isTrue();
                assertThat(batch("a",f.a,UUID.randomUUID(),"normal",f.a1).statusCode()).isEqualTo(403);
                assertThat(f.version(f.a,f.a1)).isEqualTo(1);assertThat(f.successes(f.a)).isEqualTo(1);
            }finally{ThreatFixtureController.release.countDown();}
        }
    }
    @Test void policyRevocationDuringListWaitsUntilItsTransactionEnds() throws Exception {revokeDuringList(true);}
    @Test void assignmentRevocationDuringListWaitsAndRestrictsNextList() throws Exception {revokeDuringList(false);}
    void revokeDuringList(boolean policy) throws Exception {
        ThreatFixtureController.entered=new CountDownLatch(1);ThreatFixtureController.release=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var list=workers.submit(()->f.actor("a").browser().get(ROOT+f.a+"/list?hold=true"));
            try{
                assertThat(ThreatFixtureController.entered.await(10,TimeUnit.SECONDS)).isTrue();
                var revoke=workers.submit(()->{f.admin(f.a,s->{if(policy)s.update("DELETE FROM access_control_private.role_capabilities WHERE role_code='ACCOUNTANT' AND capability='TAXPAYER_VIEW'");
                    else s.update("UPDATE access_control.resource_grants SET active=false WHERE membership_id=? AND capability='TAXPAYER_VIEW'",f.members.get("a"));});return true;});
                ThreatHarness.until(()->f.blocked(PostgresTestDatabase.MIGRATION_USER));ThreatFixtureController.release.countDown();
                assertThat(list.get().body()).contains(f.a1.toString()).doesNotContain(f.a2.toString());assertThat(revoke.get()).isTrue();
                if(policy)assertThat(list("a",f.a).statusCode()).isEqualTo(403);else assertThat(OidcBrowser.json(list("a",f.a)).path("items").size()).isZero();
            }finally{
                ThreatFixtureController.release.countDown();
                if(policy)f.admin(null,s->s.update("INSERT INTO access_control_private.role_capabilities VALUES('ACCOUNTANT','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY #30') ON CONFLICT DO NOTHING"));
            }
        }
    }
    @Test void onePhysicalRuntimeConnectionCannotInheritOtherActorOrTenant() throws Exception {
        try(var connection=runtime.getConnection()){
            connection.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            int pid=sql.queryForObject("SELECT pg_backend_pid()",Integer.class);
            for(boolean rollback:List.of(false,true))for(String name:List.of("a","b","a","b")){
                UUID tenant=name.equals("a")?f.a:f.b,resource=name.equals("a")?f.a1:f.b1;
                assertThat(sql.queryForObject("SELECT current_user",String.class)).isEqualTo(PostgresTestDatabase.RUNTIME_USER);
                assertThat(sql.queryForObject("SELECT identity.current_actor_id()",UUID.class)).isNull();
                f.authorize(sql,f.actor(name),tenant,"TAXPAYER_VIEW");
                assertThat(sql.queryForList("SELECT id FROM threat_fixture.resources",UUID.class)).containsExactly(resource);
                assertThat(sql.queryForObject("SELECT pg_backend_pid()",Integer.class)).isEqualTo(pid);
                if(rollback)connection.rollback();else connection.commit();
            }
            assertThat(sql.queryForList("SELECT id FROM threat_fixture.resources",UUID.class)).isEmpty();connection.rollback();
        }
    }
    @Test void runtimeSqlWithoutWhereCannotModifyOrDeleteOtherPortfolio() throws Exception {
        try(var connection=runtime.getConnection()){
            connection.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            f.authorize(sql,f.actor("a"),f.a,"TAXPAYER_EDIT");
            assertThat(sql.update("UPDATE threat_fixture.resources SET version=version+1")).isEqualTo(1);
            assertThat(sql.update("DELETE FROM threat_fixture.resources")).isEqualTo(1);
            connection.rollback();
        }
        assertThat(f.version(f.a,f.a2)).isZero();assertThat(f.version(f.b,f.b1)).isZero();
    }
    @Test void forgedGucsActorAndCrossTenantForeignKeyCannotAuthorize() throws Exception {
        try(var connection=runtime.getConnection()){
            connection.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            sql.queryForObject("SELECT set_config('app.current_tenant_id',?,true)",String.class,f.b.toString());
            sql.queryForObject("SELECT set_config('app.current_tenant_transaction',pg_current_xact_id()::text,true)",String.class);
            assertThat(sql.queryForList("SELECT id FROM threat_fixture.resources",UUID.class)).isEmpty();
            assertThatThrownBy(()->sql.queryForList("SELECT * FROM identity.bind_actor(?,?,0,repeat('0',64))",OIDC.issuer(),f.actor("b").username())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            connection.rollback();f.authorize(sql,f.actor("a"),f.a,"TAXPAYER_VIEW");
            assertThatThrownBy(()->sql.update("INSERT INTO threat_fixture.links VALUES(?,?,?)",f.a,UUID.randomUUID(),f.b1)).satisfies(t->assertThat(state(t)).isEqualTo("23503"));
            connection.rollback();
            assertThat(sql.queryForObject("SELECT identity.current_actor_id()",UUID.class)).isNull();connection.rollback();
        }
    }
    @Test void requiresNewDoesNotInheritOrReuseTheOuterAuthority() throws Exception {
        var response=f.actor("a").browser().get(ROOT+f.a+"/requires-new");assertThat(response.statusCode()).isEqualTo(200);var json=OidcBrowser.json(response);
        assertThat(json.path("inner").path("denied").asBoolean()).isTrue();assertThat(json.path("inner").path("rows").asInt()).isZero();
        assertThat(json.path("inner").path("innerPid").asInt()).isNotEqualTo(json.path("outerPid").asInt());
        assertThat(json.path("restoredPid").asInt()).isEqualTo(json.path("outerPid").asInt());
    }
    @Test void retainedAuthorityAndActorTenantOrCapabilityChangesFailClosed() throws Exception {
        assertThat(f.actor("multi").browser().get(ROOT+f.a+"/switch/"+f.b).statusCode()).isEqualTo(403);
        assertThat(f.actor("a").browser().get(ROOT+f.a+"/switch-capability").statusCode()).isEqualTo(403);
        assertThat(f.actor("a").browser().get(ROOT+f.a+"/retained").statusCode()).isEqualTo(403);
        try(var connection=runtime.getConnection()){
            connection.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(connection,true));f.bind(sql,f.actor("a"));
            assertThatThrownBy(()->f.bind(sql,f.actor("b"))).satisfies(t->assertThat(state(t)).isEqualTo("42501"));connection.rollback();
        }
        assertPortfolio("multi",f.a,f.a1);assertPortfolio("multi",f.b,f.b1);
    }
    @Test void virtualAndReusedPlatformThreadsDoNotReceiveDatabaseAuthority() throws Exception {
        var response=f.actor("a").browser().get(ROOT+f.a+"/threads");assertThat(response.statusCode()).isEqualTo(200);
        assertThat(OidcBrowser.json(response).path("denied").asBoolean()).isTrue();assertThat(OidcBrowser.json(response).path("rows").asInt()).isZero();
        try(var workers=Executors.newFixedThreadPool(2)){
            for(int i=0;i<12;i++){String name=i%2==0?"a":"b";UUID tenant=i%2==0?f.a:f.b,resource=i%2==0?f.a1:f.b1;
                var result=workers.submit(()->list(name,tenant)).get();assertThat(result.body()).contains(resource.toString()).doesNotContain(f.a2.toString());}
        }
    }
    @Test void auditAndTransportCannotBecomeAResourceDirectory() throws Exception {
        UUID event=event(batch("a",f.a,UUID.randomUUID(),"normal",f.a1));
        for(String name:List.of("a","b","owner"))assertThat(f.actor(name).browser().get("/api/v1/audit").statusCode()).isEqualTo(403);
        try(var a=f.new Transport(f.a);var b=f.new Transport(f.b)){
            var claims=a.protocol.claim(100,30000);assertThat(claims).allSatisfy(ticket->assertThat(ticket.tenantId()).isEqualTo(f.a));
            var own=claims.stream().filter(t->t.eventId().equals(event)).findFirst().orElseThrow();
            assertThatThrownBy(()->b.protocol.process(own,()->b.receipt(event))).isInstanceOf(ApiFailure.class);
            for(String query:List.of("SELECT * FROM app.tenants","SELECT * FROM identity.memberships","SELECT * FROM journal.events","SELECT * FROM threat_fixture.resources"))
                assertThatThrownBy(()->a.sql.execute(query)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            a.protocol.process(own,()->a.receipt(event));
        }
        try(var connection=runtime.getConnection();var s=connection.createStatement()){
            assertThatThrownBy(()->s.executeQuery("SELECT * FROM journal.events")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeQuery("SELECT * FROM journal.claim(1,30000)")).isInstanceOf(SQLException.class);
        }
    }
    @Test void revokedTransportScopeCannotUseAnAlreadyClaimedEvent() throws Exception {
        UUID event=event(batch("a",f.a,UUID.randomUUID(),"normal",f.a1));
        try(var consumer=f.new Transport(f.a)){
            var ticket=consumer.protocol.claim(100,30000).stream().filter(t->t.eventId().equals(event)).findFirst().orElseThrow();
            f.admin(null,s->s.update("UPDATE journal_private.delivery_scopes SET active=false WHERE role_oid=?",consumer.roleOid));
            assertThatThrownBy(()->consumer.protocol.process(ticket,()->consumer.receipt(event))).isInstanceOf(ApiFailure.class);
            assertThat(f.rows(null,"SELECT * FROM threat_fixture.receipts WHERE event_id=?",event)).isEmpty();
        }
    }
    @Test void competingRecoveryAndOldLeaseTokensCannotDuplicateEffects() throws Exception {
        UUID event=event(batch("a",f.a,UUID.randomUUID(),"normal",f.a1));
        try(var consumer=f.new Transport(f.a);var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var old=consumer.protocol.claim(100,30000).stream().filter(t->t.eventId().equals(event)).findFirst().orElseThrow();
            f.admin(f.a,s->s.update("UPDATE journal.outbox SET leased_until=clock_timestamp()-interval '1 second' WHERE event_id=?",event));
            var barrier=new CyclicBarrier(2);Callable<List<OutboxProtocol.Claim>> recover=()->{barrier.await();return consumer.protocol.claim(1,30000);};
            var first=workers.submit(recover);var second=workers.submit(recover);var tickets=new ArrayList<OutboxProtocol.Claim>();tickets.addAll(first.get());tickets.addAll(second.get());
            assertThat(tickets).hasSize(1);assertThat(tickets.getFirst().eventId()).isEqualTo(event);assertThat(tickets.getFirst().leaseToken()).isNotEqualTo(old.leaseToken());
            assertThat(consumer.protocol.retry(old,OutboxProtocol.Failure.RETRYABLE)).isFalse();
            assertThatThrownBy(()->consumer.protocol.process(old,()->consumer.receipt(event))).isInstanceOf(ApiFailure.class);
            consumer.protocol.process(tickets.getFirst(),()->consumer.receipt(event));
            assertThat(f.rows(null,"SELECT * FROM threat_fixture.receipts WHERE event_id=?",event)).hasSize(1);
        }
    }
    @Test void consumerLocalEffectAndAckAreOneTransaction() throws Exception {
        UUID event=event(batch("a",f.a,UUID.randomUUID(),"normal",f.a1));
        try(var consumer=f.new Transport(f.a)){
            var ticket=consumer.protocol.claim(100,30000).stream().filter(t->t.eventId().equals(event)).findFirst().orElseThrow();
            assertThatThrownBy(()->consumer.protocol.process(ticket,()->{consumer.receipt(event);throw new IllegalStateException("SYNTHETIC rollback");})).isInstanceOf(IllegalStateException.class);
            assertThat(f.rows(null,"SELECT * FROM threat_fixture.receipts WHERE event_id=?",event)).isEmpty();
            assertThat(f.rows(f.a,"SELECT status FROM journal.outbox WHERE event_id=?",event).getFirst().get("status")).isEqualTo("PROCESSING");
            consumer.protocol.process(ticket,()->consumer.receipt(event));
            assertThat(f.rows(f.a,"SELECT status FROM journal.outbox WHERE event_id=?",event).getFirst().get("status")).isEqualTo("DELIVERED");
        }
    }

    @Test void removingResourceFenceIsCaughtByTheRealPortfolioOracle() throws Exception {
        assertPortfolio("a",f.a,f.a1);
        String policy=(String)f.rows(null,"SELECT pg_get_expr(polqual,polrelid) AS expression FROM pg_policy WHERE polrelid='threat_fixture.resources'::regclass AND polname='resource_fence'").getFirst().get("expression");
        String check=(String)f.rows(null,"SELECT pg_get_expr(polwithcheck,polrelid) AS expression FROM pg_policy WHERE polrelid='threat_fixture.resources'::regclass AND polname='resource_fence'").getFirst().get("expression");
        try {
            f.admin(null,s->s.execute("DROP POLICY resource_fence ON threat_fixture.resources"));
            assertThatThrownBy(()->assertPortfolio("a",f.a,f.a1)).isInstanceOf(AssertionError.class);
        }finally{f.admin(null,s->s.execute("CREATE POLICY resource_fence ON threat_fixture.resources AS RESTRICTIVE TO fiscuvia_runtime USING("+policy+") WITH CHECK("+check+")"));}
        assertPortfolio("a",f.a,f.a1);
    }
    @Test void removingTenantFenceOrForceRlsIsCaughtByStartupOracle() throws Exception {
        var guard=context.getBean("verifyRuntimeDatabase",InitializingBean.class);guard.afterPropertiesSet();
        try {
            f.admin(null,s->s.execute("ALTER TABLE app.tenants NO FORCE ROW LEVEL SECURITY"));
            assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
        }finally{f.admin(null,s->s.execute("ALTER TABLE app.tenants FORCE ROW LEVEL SECURITY"));}
        try {
            f.admin(null,s->s.execute("ALTER POLICY tenant_fence ON app.tenants USING(true) WITH CHECK(true)"));
            assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
        }finally{f.admin(null,s->s.execute("ALTER POLICY tenant_fence ON app.tenants USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id())"));}
        guard.afterPropertiesSet();
    }
    @Test void excessiveRuntimeGrantsAndOutboxExecutionAreCaughtByJournalOracle() throws Exception {
        var guard=context.getBean("verifyJournal",InitializingBean.class);guard.afterPropertiesSet();
        for(String privilege:List.of("SELECT","UPDATE")){
            try{f.admin(null,s->s.execute("GRANT "+privilege+" ON journal.events TO fiscuvia_runtime"));
                assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(RuntimeException.class);
            }finally{f.admin(null,s->s.execute("REVOKE "+privilege+" ON journal.events FROM fiscuvia_runtime"));}
        }
        try{f.admin(null,s->s.execute("GRANT EXECUTE ON FUNCTION journal.claim(integer,integer) TO fiscuvia_runtime"));
            assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(RuntimeException.class);
        }finally{f.admin(null,s->s.execute("REVOKE EXECUTE ON FUNCTION journal.claim(integer,integer) FROM fiscuvia_runtime"));}
        guard.afterPropertiesSet();
    }
    @Test void bypassRlsMutationIsCaughtByGuardAndRuntimeRowOracle() throws Exception {
        var guard=context.getBean("verifyRuntimeDatabase",InitializingBean.class);guard.afterPropertiesSet();
        try {
            bootstrapDdl("ALTER ROLE fiscuvia_runtime BYPASSRLS");
            assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->assertPortfolio("a",f.a,f.a1)).isInstanceOf(AssertionError.class);
        }finally{bootstrapDdl("ALTER ROLE fiscuvia_runtime NOBYPASSRLS");}
        guard.afterPropertiesSet();assertPortfolio("a",f.a,f.a1);
    }
    @Test void unsafeDefinerSearchPathIsCaughtAndRestored() throws Exception {
        assertDefiners();String definition=f.function("access_control.resource_allowed(uuid,text,uuid,text)");
        try{f.admin(null,s->s.execute("ALTER FUNCTION access_control.resource_allowed(uuid,text,uuid,text) SET search_path=public,pg_temp"));
            assertThatThrownBy(this::assertDefiners).isInstanceOf(AssertionError.class);
        }finally{f.definition(definition);}
        assertDefiners();
    }
    @Test void skippingMembershipChecksIsCaughtByTheHttpOracle() throws Exception {
        f.admin(f.a,s->s.update("UPDATE identity.memberships SET status='SUSPENDED' WHERE id=?",f.members.get("a")));
        assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(404);
        String authorization=f.function("access_control.authorize_operation(uuid,text,text,uuid)"),tenant=f.function("identity_private.authorize_active_membership(uuid,boolean)");
        try {
            f.definition(authorization.replace("AND status='ACTIVE'",""));f.definition(tenant.replace("AND status='ACTIVE'",""));
            assertThatThrownBy(()->assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(404)).isInstanceOf(AssertionError.class);
        }finally{f.definition(authorization);f.definition(tenant);}
        assertThat(item("a",f.a,f.a1).statusCode()).isEqualTo(404);
    }
    @Test void relaxingMfaPolicyIsCaughtByTheHttpOracle() throws Exception {
        f.grant(f.a,f.members.get("a"),f.a1,"TAXPAYER_EXPORT");
        Runnable restore=ThreatHarness.principalField(sessions,f.actor("a"),"mfaAt",0L);
        try {
            assertThat(f.actor("a").browser().get(ROOT+f.a+"/export").statusCode()).isEqualTo(403);
            try{f.admin(null,s->s.update("UPDATE access_control_private.role_capabilities SET requires_mfa=false WHERE role_code='ACCOUNTANT' AND capability='TAXPAYER_EXPORT'"));
                assertThatThrownBy(()->assertThat(f.actor("a").browser().get(ROOT+f.a+"/export").statusCode()).isEqualTo(403)).isInstanceOf(AssertionError.class);
            }finally{f.admin(null,s->s.update("UPDATE access_control_private.role_capabilities SET requires_mfa=true WHERE role_code='ACCOUNTANT' AND capability='TAXPAYER_EXPORT'"));}
            assertThat(f.actor("a").browser().get(ROOT+f.a+"/export").statusCode()).isEqualTo(403);
        }finally{restore.run();}
    }
    @Test void absentFutureFileCacheAndJobApisCannotBecomePublicBackdoors() throws Exception {
        try(var anonymous=new OidcBrowser(TLS,OIDC)){
            assertThat(anonymous.get("/api/v1/__threat-denied-route").statusCode()).isEqualTo(404);
            assertThat(f.actor("owner").browser().get("/api/v1/__threat-denied-route").statusCode()).isEqualTo(403);
            for(String route:List.of("/api/v1/documents/download","/api/v1/files/DEMO-private","/api/v1/exports","/api/v1/jobs/run","/api/v1/authorize?userId="+f.actor("b").user())){
                assertThat(anonymous.get(route).statusCode()).isIn(401,403,404);
                assertThat(f.actor("owner").browser().get(route).statusCode()).isIn(403,404);
            }
        }
    }
    @Test void adversarialRequestsAndJournalKeepCanariesOutOfOperationalOutput(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String secret="DEMO-SECRET-"+UUID.randomUUID();
        var request=HttpRequest.newBuilder(URI.create(TLS.origin()+ROOT+f.a+"/item/"+f.a2+"?private="+secret))
            .header("Authorization","Bearer "+secret).header("X-Correlation-Id",secret).header("X-Private-Rfc",f.demoRfc).GET().build();
        var response=f.actor("a").browser().client.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(404);
        batch("a",f.a,UUID.randomUUID(),"normal",f.a1);
        assertThat(output.getAll()).doesNotContain(secret,f.demoRfc,f.actor("a").browser().sessionCookie(),OIDC.clientSecret,DATABASE.actorKey);
        assertThat(DATABASE.getLogs()).doesNotContain(secret,f.demoRfc,OIDC.clientSecret,DATABASE.actorKey);
        assertThat(response.body()).doesNotContain(secret,f.demoRfc,"SQLException","SELECT ","stackTrace");
    }
    void assertDefiners() throws Exception {
        var functions=f.rows(null,"SELECT pg_get_userbyid(p.proowner) AS owner,p.proconfig AS config FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE p.prosecdef AND n.nspname IN ('app','identity','identity_private','access_control','access_control_private','journal','journal_private')");
        assertThat(functions).isNotEmpty();
        for(var function:functions){assertThat(function.get("owner")).isEqualTo(PostgresTestDatabase.MIGRATION_USER);
            assertThat(Arrays.toString((String[])((java.sql.Array)function.get("config")).getArray())).contains("search_path=pg_catalog, pg_temp");}
    }
    void bootstrapDdl(String command) throws Exception {
        // Fixture bootstrap performs DDL only; every data/assertion HTTP operation still uses runtime.
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var s=c.createStatement()){s.execute(command);}
    }
    HttpResponse<String> list(String actor,UUID tenant) throws Exception{return f.actor(actor).browser().get(ROOT+tenant+"/list");}
    HttpResponse<String> item(String actor,UUID tenant,UUID resource) throws Exception{return f.actor(actor).browser().get(ROOT+tenant+"/item/"+resource);}
    HttpResponse<String> batch(String actor,UUID tenant,UUID command,String mode,UUID... ids) throws Exception {
        return f.actor(actor).browser().send("POST",ROOT+tenant+"/batch/"+command+"?mode="+mode,body(ids),f.actor(actor).browser().csrf());
    }
    static String body(UUID... ids){return "{\"ids\":["+Arrays.stream(ids).map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(","))+"]}";}
    static String inviteBody(UUID tenant){return "{\"tenantId\":\""+tenant+"\",\"subject\":\"DEMO-recipient\",\"role\":\"ACCOUNTANT\"}";}
    static UUID event(HttpResponse<String> response){assertThat(response.statusCode()).isEqualTo(200);return UUID.fromString(OidcBrowser.json(response).path("eventId").asText());}
    void assertPortfolio(String actor,UUID tenant,UUID expected) throws Exception {
        var response=list(actor,tenant);assertThat(response.statusCode()).isEqualTo(200);var json=OidcBrowser.json(response);
        assertThat(json.path("count").asInt()).isEqualTo(1);assertThat(json.path("items").size()).isEqualTo(1);assertThat(json.path("items").get(0).asText()).isEqualTo(expected.toString());
    }
    static String state(Throwable error){while(!(error instanceof SQLException)&&error.getCause()!=null)error=error.getCause();return error instanceof SQLException sql?sql.getSQLState():"";}
}
