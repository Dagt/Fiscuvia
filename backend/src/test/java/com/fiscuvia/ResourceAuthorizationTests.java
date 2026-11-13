package com.fiscuvia;

import com.fiscuvia.identity.*;
import com.fiscuvia.platform.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Real OIDC session -> fresh authorization -> runtime PostgreSQL rows. No authentication mocks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ResourceAuthorizationTests.ResourceFixtureController.class) @Timeout(90)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ResourceAuthorizationTests {
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DATABASE=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    static final String ROOT="/api/v1/auth/invitations/__resource-test/";
    @Autowired DataSource source;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.session.SessionRepository<? extends org.springframework.session.Session> sessions;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        DATABASE.register(r); TLS.register(r); r.add("fiscuvia.identity.enabled",()->true);
        r.add("fiscuvia.identity.issuer",OIDC::issuer); r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);
        r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");
        r.add("spring.datasource.hikari.maximum-pool-size",()->2);
    }
    static boolean fixtureInstalled;
    @BeforeEach void schema() throws Exception {
        if(fixtureInstalled)return;
        // Spring context has not necessarily migrated at BeforeAll. Flyway same immutable sources.
        org.flywaydb.core.Flyway.configure().dataSource(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword)
          .defaultSchema("infrastructure").locations("classpath:db/migration").load().migrate();
        try(var c=migration();var s=c.createStatement();var in=ResourceAuthorizationTests.class.getResourceAsStream("/resource-authorization-fixture.sql")) {
            s.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        fixtureInstalled=true;
    }
    @Test void anonymousAndExpiredSessionCannotReachResource() throws Exception {
        try(var a=new OidcBrowser(TLS,OIDC)) {
            assertThat(a.get(ROOT+UUID.randomUUID()+"/list").statusCode()).isEqualTo(401);
            a.login("alice"); alterPrincipal(a,"absoluteExpiry",Instant.now().minusSeconds(1).getEpochSecond());
            assertThat(a.get(ROOT+UUID.randomUUID()+"/list").statusCode()).isEqualTo(401);
        }
    }
    @Test void disabledGlobalIdentityInvalidatesResourceAccess() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            admin(null,s->s.update("UPDATE identity_private.users SET status='DISABLED' WHERE id=?",f.user));
            assertThat(f.get("list").statusCode()).isEqualTo(401);
        }
    }
    @Test void suspendedMembershipDeniesFreshOperation() throws Exception { inactive("SUSPENDED"); }
    @Test void revokedMembershipDeniesFreshOperation() throws Exception { inactive("REVOKED"); }
    void inactive(String status) throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            admin(f.tenant,s->s.update("UPDATE identity.memberships SET status=? WHERE id=?",status,f.member));
            assertThat(f.get("list").statusCode()).isEqualTo(403);
        }
    }
    @Test void otherTenantReadAndEditConcealKnownUuid() throws Exception {
        try(var a=new Fixture("ACCOUNTANT");var b=new Fixture("ACCOUNTANT","bob")) {
            assertThat(a.browser.get(ROOT+b.tenant+"/item/"+b.own).statusCode()).isEqualTo(404);
            assertThat(a.browser.send("POST",ROOT+b.tenant+"/edit/"+b.own,"{}",a.browser.csrf()).statusCode()).isEqualTo(404);
            assertThat(b.get("item/"+b.own).statusCode()).isEqualTo(200);
        }
    }
    @Test void twoAccountantsInOneTenantHaveDifferentPortfolios() throws Exception {
        try(var a=new Fixture("ACCOUNTANT");var b=new OidcBrowser(TLS,OIDC)) {
            UUID user=id(b.login("bob")); UUID member=membership(a.tenant,user,"ACCOUNTANT");
            grant(a.tenant,member,a.other,"TAXPAYER_VIEW","TAXPAYER");
            assertThat(a.get("item/"+a.other).statusCode()).isEqualTo(404);
            assertThat(b.get(ROOT+a.tenant+"/item/"+a.own).statusCode()).isEqualTo(404);
            assertThat(OidcBrowser.json(b.get(ROOT+a.tenant+"/list")).toString()).contains(a.other.toString()).doesNotContain(a.own.toString());
        }
    }
    @Test void readGrantDoesNotAllowEditOrDeleteEvenViaRawSql() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(404);
            assertThat(OidcBrowser.json(f.get("raw-write/"+f.own)).path("changed").asInt()).isZero();
            assertThat(f.get("item/"+f.own).body()).contains("DEMO own");
        }
    }
    @Test void editGrantDoesNotImplyExport() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            grant(f.tenant,f.member,f.own,"TAXPAYER_EDIT","TAXPAYER");
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(200);
            assertThat(f.get("export").body()).isEqualTo("[]");
        }
    }
    @Test void assistantCannotManageTenantOrInvite() throws Exception {
        try(var f=new Fixture("ASSISTANT")) {
            assertThat(f.get("admin").statusCode()).isEqualTo(403);
            assertThat(f.browser.send("POST","/api/v1/auth/invitations","{\"tenantId\":\""+f.tenant+"\",\"subject\":\"DEMO\",\"role\":\"ACCOUNTANT\"}",f.browser.csrf()).statusCode()).isEqualTo(403);
        }
    }
    @Test void supervisorHasNoAutomaticPortfolioOrAdministrativeAccess() throws Exception {
        try(var f=new Fixture("FIRM_SUPERVISOR")) {
            admin(f.tenant,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE membership_id=?",f.member));
            assertThat(f.get("item/"+f.own).statusCode()).isEqualTo(404);
            assertThat(f.get("list").body()).isEqualTo("[]");
            assertThat(f.get("admin").statusCode()).isEqualTo(403);
        }
    }
    @Test void approvedOwnerPolicyStillRequiresAnExactFiscalGrant() throws Exception {
        try(var f=new Fixture("FIRM_OWNER_ADMIN")) {
            var response=f.get("list");assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(f.own.toString()).doesNotContain(f.other.toString());
        }
    }
    @Test void ownerCannotUseReadContextToInvokeAdministrativeSqlCommand() throws Exception {
        try(var f=new Fixture("FIRM_OWNER_ADMIN","bob")) {
            assertThat(f.get("raw-invite").statusCode()).isEqualTo(403);
        }
    }
    @Test void ownerMfaDoesNotGrantDirectoryOfOtherMembersBySql() throws Exception {
        try(var f=new Fixture("FIRM_OWNER_ADMIN","bob");var other=new OidcBrowser(TLS,OIDC)) {
            UUID peer=membership(f.tenant,id(other.login("carol")),"ACCOUNTANT");
            var result=f.get("raw-member-directory");
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).contains(f.member.toString()).doesNotContain(peer.toString());
        }
    }
    @Test void unconfiguredCapabilityAndUnknownRoleFailClosed() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            assertThat(f.get("admin").statusCode()).isEqualTo(403);
            assertThatThrownBy(()->admin(f.tenant,s->s.update("UPDATE identity.membership_roles SET role_code='UNKNOWN' WHERE membership_id=?",f.member)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            admin(f.tenant,s->s.update("DELETE FROM identity.membership_roles WHERE membership_id=?",f.member));
            assertThat(f.get("list").statusCode()).isEqualTo(403);
        }
    }
    @Test void bodyAndHeadersCannotGrantRolesCapabilitiesOrActor() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(TLS.origin()+ROOT+f.tenant+"/edit/"+f.other))
                .header("X-CSRF-TOKEN",f.browser.csrf()).header("Content-Type","application/json")
                .header("X-Role","FIRM_OWNER_ADMIN").header("X-User-Id",UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"role\":\"FIRM_OWNER_ADMIN\",\"capability\":\"ALL\",\"authorized\":true}")).build();
            assertThat(f.browser.client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
    }
    @Test void guessedAndUnassignedUuidsAreIndistinguishable() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            var missing=f.get("item/"+UUID.randomUUID()); var denied=f.get("item/"+f.other);
            assertThat(missing.statusCode()).isEqualTo(404); assertThat(denied.statusCode()).isEqualTo(404);
            assertThat(OidcBrowser.json(missing).path("detail")).isEqualTo(OidcBrowser.json(denied).path("detail"));
        }
    }
    @Test void missingAssignmentAndTenantFiltersStillRestrictSearchCountAndPagination() throws Exception {
        try(var a=new Fixture("ACCOUNTANT");var b=new Fixture("ACCOUNTANT","bob")) {
            assertThat(a.get("list").body()).contains(a.own.toString()).doesNotContain(a.other.toString(),b.own.toString());
            assertThat(OidcBrowser.json(a.get("count")).path("count").asInt()).isEqualTo(1);
            assertThat(a.get("page?offset=1").body()).isEqualTo("[]");
        }
    }
    @Test void downloadsAndExportsRestrictRowsBeforeGeneratingOutput() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            UUID document=f.resource("DOCUMENT"),otherDocument=f.resource("DOCUMENT"),report=f.resource("REPORT");
            grant(f.tenant,f.member,document,"DOCUMENT_DOWNLOAD","DOCUMENT"); grant(f.tenant,f.member,report,"REPORT_EXPORT","REPORT");
            grant(f.tenant,f.member,f.own,"TAXPAYER_EXPORT","TAXPAYER");
            assertThat(f.get("download/"+document).statusCode()).isEqualTo(200);
            assertThat(f.get("download/"+otherDocument).statusCode()).isEqualTo(404);
            assertThat(f.get("export").body()).contains(f.own.toString()).doesNotContain(f.other.toString());
            assertThat(f.get("reports").body()).contains(report.toString()).doesNotContain(f.own.toString(),otherDocument.toString());
        }
    }
    @Test void revokedAssignmentIsRecheckedOnEveryRequest() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            assertThat(f.get("item/"+f.own).statusCode()).isEqualTo(200);
            admin(f.tenant,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE membership_id=?",f.member));
            assertThat(f.get("item/"+f.own).statusCode()).isEqualTo(404);
            assertThat(f.get("list").body()).isEqualTo("[]");
        }
    }
    @Test void concurrentAssignmentRevocationCommitsBeforeAuthorizationAndDenies() throws Exception { revocationWins("assignment"); }
    @Test void concurrentMembershipRevocationCommitsBeforeAuthorizationAndDenies() throws Exception { revocationWins("membership"); }
    @Test void concurrentPolicyRevocationCommitsBeforeAuthorizationAndDenies() throws Exception { revocationWins("policy"); }
    void revocationWins(String type) throws Exception {
        try(var f=new Fixture("ACCOUNTANT");var c=migration();var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            c.setAutoCommit(false); var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,f.tenant);
            if(type.equals("assignment")) sql.update("UPDATE access_control.resource_grants SET active=false WHERE membership_id=?",f.member);
            if(type.equals("membership")) sql.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",f.member);
            if(type.equals("policy")) sql.update("DELETE FROM access_control_private.role_capabilities WHERE role_code='ACCOUNTANT' AND capability='TAXPAYER_VIEW'");
            var response=executor.submit(()->f.get("item/"+f.own));
            // Observe a real PostgreSQL lock wait rather than assuming a thread has reached the check.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
            while(System.nanoTime()<deadline) {
                try(var probe=migration();var s=probe.createStatement();var rows=s.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.usename='fiscuvia_runtime' AND NOT l.granted)")) {
                    rows.next(); if(rows.getBoolean(1)) { waiting=true; break; }
                } Thread.sleep(20);
            }
            assertThat(waiting).isTrue(); c.commit(); assertThat(response.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(404);
            if(type.equals("policy")) admin(null,s->s.update("INSERT INTO access_control_private.role_capabilities VALUES('ACCOUNTANT','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC TEST ONLY')"));
        }
    }
    @Test void parallelSessionsAndPoolReuseDoNotInheritActorOrTenant() throws Exception {
        try(var a=new Fixture("ACCOUNTANT");var b=new Fixture("ACCOUNTANT","bob");var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=new ArrayList<Future<String>>();
            for(int i=0;i<16;i++) { var f=i%2==0?a:b; tasks.add(executor.submit(()->f.get("list").body())); }
            for(int i=0;i<tasks.size();i++) { var f=i%2==0?a:b; var other=i%2==0?b:a;
                assertThat(tasks.get(i).get()).contains(f.own.toString()).doesNotContain(other.own.toString(),f.other.toString()); }
            try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT app.current_tenant_id(),identity.current_actor_id(),access_control.resource_allowed(null,'TAXPAYER',null,'TAXPAYER_VIEW')")) {
                r.next(); assertThat(r.getObject(1)).isNull(); assertThat(r.getObject(2)).isNull(); assertThat(r.getBoolean(3)).isFalse();
            }
        }
    }
    @Test void oneVerifiedIdentityCanUseTwoTenantsWithoutSharingPortfolio() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            UUID second=tenant(); UUID membership=membership(second,f.user,"ACCOUNTANT"); UUID record=resource(second,"TAXPAYER");
            grant(second,membership,record,"TAXPAYER_VIEW","TAXPAYER");
            assertThat(f.browser.get(ROOT+second+"/list").body()).contains(record.toString()).doesNotContain(f.own.toString());
            assertThat(f.get("item/"+record).statusCode()).isEqualTo(404);
            assertThat(f.get("list").body()).contains(f.own.toString()).doesNotContain(record.toString());
        }
    }
    @Test void directSqlCannotForgeActorOperationOrModifyGrants() throws Exception {
        try(var f=new Fixture("ACCOUNTANT");var c=source.getConnection()) {
            var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            assertThat(sql.queryForObject("SELECT count(*) FROM resource_fixture.items",Integer.class)).isZero();
            assertThat(sql.queryForObject("SELECT access_control.authorize_operation(?,'TAXPAYER_VIEW','TAXPAYER',?)",String.class,f.tenant,f.own)).isEqualTo("CONTEXT_REJECTED");
            assertThatThrownBy(()->sql.queryForList("SELECT * FROM identity.bind_actor('fake','fake',0,repeat('0',64))")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.update("UPDATE access_control.resource_grants SET active=true")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.update("UPDATE access_control_private.role_capabilities SET requires_mfa=false")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void privilegedCapabilityRequiresFreshVerifiedMfa() throws Exception {
        for(long evidence:new long[]{0,Instant.now().minusSeconds(700).getEpochSecond(),Instant.now().plusSeconds(900).getEpochSecond()}) {
            try(var f=new Fixture("FIRM_OWNER_ADMIN","carol")) {
                String csrf=f.browser.csrf();
                alterPrincipal(f.browser,"mfaAt",evidence);
                assertThat(f.browser.send("POST","/api/v1/auth/invitations","{\"tenantId\":\""+f.tenant+"\",\"subject\":\"DEMO\",\"role\":\"ACCOUNTANT\"}",csrf).statusCode()).isIn(401,403);
            }
        }
    }
    @Test void failuresAndDiagnosticsDoNotRevealSensitiveIdentifiers(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            String canary="DEMO900101XX1-private-resource";
            var response=f.browser.get(ROOT+f.tenant+"/item/"+f.other+"?private="+canary);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain(canary,f.other.toString(),f.tenant.toString(),"SQL","role_code");
            assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
            assertThat(output.getAll()).contains("ACCESS_DECISION","RESOURCE_UNASSIGNED","DENY").doesNotContain(canary,OIDC.password,OIDC.clientSecret,DATABASE.actorKey,f.other.toString());
        }
    }
    @Test void removalOfResourceFenceIsDetectedByRealRowOracle() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            assertPortfolio(f);
            admin(null,s->s.execute("DROP POLICY resource_fence ON resource_fixture.items"));
            try { assertThatThrownBy(()->assertPortfolio(f)).isInstanceOf(AssertionError.class); }
            finally { admin(null,s->s.execute("CREATE POLICY resource_fence ON resource_fixture.items AS RESTRICTIVE TO fiscuvia_runtime USING(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_VIEW') OR access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT') OR access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EXPORT') OR access_control.resource_allowed(tenant_id,kind,id,'DOCUMENT_DOWNLOAD') OR access_control.resource_allowed(tenant_id,kind,id,'REPORT_EXPORT')) WITH CHECK(access_control.resource_allowed(tenant_id,kind,id,'TAXPAYER_EDIT'))")); }
            assertPortfolio(f);
        }
    }
    void assertPortfolio(Fixture f) throws Exception { assertThat(OidcBrowser.json(f.get("list")).size()).isEqualTo(1); }
    @Test void resourceStateAndVersionRemainServerChecked() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            grant(f.tenant,f.member,f.own,"TAXPAYER_EDIT","TAXPAYER");
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(200);
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(409);
            admin(f.tenant,s->s.update("UPDATE resource_fixture.items SET state='CLOSED' WHERE id=?",f.own));
            assertThat(f.get("item/"+f.own).statusCode()).isEqualTo(404);
        }
    }
    @Test void retainedScopeAndNestedOperationSwitchAreRejectedAndRollback() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            assertThat(f.get("retained").statusCode()).isEqualTo(403);
            assertThat(f.get("nested/"+f.own).statusCode()).isEqualTo(403);
            assertThat(f.get("list").statusCode()).isEqualTo(200);
        }
    }
    @Test void assignmentForeignKeyCannotReferenceMembershipOfAnotherTenant() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            UUID other=tenant();
            assertThatThrownBy(()->grant(other,f.member,UUID.randomUUID(),"TAXPAYER_VIEW","TAXPAYER"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
    @Test void authorizationGuardRejectsForceRlsDrift() throws Exception {
        var guard=context.getBean("verifyIdentityRuntime",org.springframework.beans.factory.InitializingBean.class);
        admin(null,s->s.execute("ALTER TABLE access_control.resource_grants NO FORCE ROW LEVEL SECURITY"));
        try { assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(StartupGuardFailure.class); }
        finally { admin(null,s->s.execute("ALTER TABLE access_control.resource_grants FORCE ROW LEVEL SECURITY")); }
        guard.afterPropertiesSet();
    }
    @Test void authorizationGuardRejectsDirectRuntimeGrantAccess() throws Exception {
        var guard=context.getBean("verifyIdentityRuntime",org.springframework.beans.factory.InitializingBean.class);
        admin(null,s->s.execute("GRANT SELECT ON access_control.resource_grants TO fiscuvia_runtime"));
        try { assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(StartupGuardFailure.class); }
        finally { admin(null,s->s.execute("REVOKE SELECT ON access_control.resource_grants FROM fiscuvia_runtime")); }
        guard.afterPropertiesSet();
    }
    @Test void failedCommandRollsBackAllAuthorizedWrites() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            grant(f.tenant,f.member,f.own,"TAXPAYER_EDIT","TAXPAYER");
            assertThat(f.post("rollback/"+f.own).statusCode()).isEqualTo(409);
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(200);
        }
    }
    @Test void mixedBatchDeniesAtomicallyWithoutChangingAssignedResource() throws Exception {
        try(var f=new Fixture("ACCOUNTANT")) {
            grant(f.tenant,f.member,f.own,"TAXPAYER_EDIT","TAXPAYER");
            assertThat(f.post("batch/"+f.own+"/"+f.other).statusCode()).isEqualTo(404);
            assertThat(f.post("edit/"+f.own).statusCode()).isEqualTo(200);
        }
    }
    @Test void authorizedOperationHoldsAssignmentUntilCommitThenRevocationTakesEffect() throws Exception {
        try(var f=new Fixture("ACCOUNTANT");var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            ResourceFixtureController.entered=new CountDownLatch(1); ResourceFixtureController.release=new CountDownLatch(1);
            var operation=executor.submit(()->f.get("hold"));
            try {
                assertThat(ResourceFixtureController.entered.await(10,TimeUnit.SECONDS)).isTrue();
                var revoke=executor.submit(()->{ admin(f.tenant,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE membership_id=?",f.member)); return true; });
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
                while(System.nanoTime()<until) {
                    try(var c=migration();var s=c.createStatement();var r=s.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.usename='fiscuvia_migrator' AND NOT l.granted)")) {
                        r.next(); if(r.getBoolean(1)){waiting=true;break;}
                    } Thread.sleep(20);
                }
                assertThat(waiting).isTrue(); assertThat(revoke.isDone()).isFalse();
                ResourceFixtureController.release.countDown();
                assertThat(operation.get(10,TimeUnit.SECONDS).statusCode()).isEqualTo(200); assertThat(revoke.get(10,TimeUnit.SECONDS)).isTrue();
                assertThat(f.get("item/"+f.own).statusCode()).isEqualTo(404);
            } finally { ResourceFixtureController.release.countDown(); }
        }
    }
    @Test void newlyGrantedResourceCannotExpandAlreadyAuthorizedCollection() throws Exception {
        try(var f=new Fixture("ACCOUNTANT");var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            ResourceFixtureController.entered=new CountDownLatch(1); ResourceFixtureController.release=new CountDownLatch(1);
            var operation=executor.submit(()->f.get("hold"));
            try {
                assertThat(ResourceFixtureController.entered.await(10,TimeUnit.SECONDS)).isTrue();
                grant(f.tenant,f.member,f.other,"TAXPAYER_VIEW","TAXPAYER");
                ResourceFixtureController.release.countDown(); var result=operation.get(10,TimeUnit.SECONDS);
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.body()).contains(f.own.toString()).doesNotContain(f.other.toString());
                assertThat(f.get("list").body()).contains(f.own.toString(),f.other.toString());
            } finally { ResourceFixtureController.release.countDown(); }
        }
    }
    static final class Fixture implements AutoCloseable {
        final OidcBrowser browser=new OidcBrowser(TLS,OIDC); final UUID user,tenant,member,own,other;
        Fixture(String role) throws Exception { this(role,"alice"); }
        Fixture(String role,String login) throws Exception {
            user=id(browser.login(login)); tenant=tenant(); member=membership(tenant,user,role);
            own=ResourceAuthorizationTests.resource(tenant,"TAXPAYER"); other=ResourceAuthorizationTests.resource(tenant,"TAXPAYER");
            grant(tenant,member,own,"TAXPAYER_VIEW","TAXPAYER");
        }
        UUID resource(String kind) throws Exception { return ResourceAuthorizationTests.resource(tenant,kind); }
        java.net.http.HttpResponse<String> get(String path) throws Exception { return browser.get(ROOT+tenant+"/"+path); }
        java.net.http.HttpResponse<String> post(String path) throws Exception { return browser.send("POST",ROOT+tenant+"/"+path,"{}",browser.csrf()); }
        public void close(){ browser.close(); }
    }
    static UUID id(tools.jackson.databind.JsonNode me) { return UUID.fromString(me.path("userId").asText()); }
    static Connection migration() throws Exception { return DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword); }
    static void admin(UUID tenant,java.util.function.Consumer<JdbcTemplate> work) throws Exception {
        try(var c=migration()) { c.setAutoCommit(false); var s=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            if(tenant!=null) s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            work.accept(s); c.commit(); }
    }
    static UUID tenant() throws Exception { UUID id=UUID.randomUUID(); admin(id,s->s.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",id)); return id; }
    static UUID membership(UUID tenant,UUID user,String role) throws Exception {
        UUID id=UUID.randomUUID(); admin(tenant,s->{ QuotaFixtures.seed(s,tenant);s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,id,user);
            s.update("INSERT INTO identity.membership_roles VALUES(?,?,?)",tenant,id,role); }); return id;
    }
    static UUID resource(UUID tenant,String kind) throws Exception { UUID id=UUID.randomUUID(); admin(tenant,s->s.update("INSERT INTO resource_fixture.items(tenant_id,id,kind,label) VALUES(?,?,?,'DEMO own')",tenant,id,kind)); return id; }
    static void grant(UUID tenant,UUID member,UUID resource,String capability,String kind) throws Exception { admin(tenant,s->TaxpayerFixtures.grant(s,tenant,member,resource,capability,kind)); }
    void alterPrincipal(OidcBrowser browser,String field,long value) {
        String id=new String(Base64.getDecoder().decode(browser.sessionCookie().replace("\"","")),java.nio.charset.StandardCharsets.UTF_8);
        edit(sessions,id,session->{
            org.springframework.security.core.context.SecurityContext old=session.getAttribute("SPRING_SECURITY_CONTEXT"); Object p=old.getAuthentication().getPrincipal();
            try { var parts=p.getClass().getRecordComponents(); Object[] values=new Object[parts.length]; Class<?>[] types=new Class<?>[parts.length];
                for(int i=0;i<parts.length;i++){ types[i]=parts[i].getType(); var get=parts[i].getAccessor(); get.setAccessible(true); values[i]=parts[i].getName().equals(field)?value:get.invoke(p); }
                var constructor=p.getClass().getDeclaredConstructor(types); constructor.setAccessible(true);
                var principal=(org.springframework.security.oauth2.core.user.OAuth2User)constructor.newInstance(values);
                var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(); context.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,principal.getAuthorities(),"local"));
                session.setAttribute("SPRING_SECURITY_CONTEXT",context);
            } catch(ReflectiveOperationException e){ throw new IllegalStateException(e); }
        });
    }
    static <S extends org.springframework.session.Session> void edit(org.springframework.session.SessionRepository<S> repository,String id,java.util.function.Consumer<S> work){ S s=repository.findById(id); work.accept(s); repository.save(s); }

    @RestController static class ResourceFixtureController {
        static volatile CountDownLatch entered,release;
        final AuthorizedTenantOperations operations; final JdbcTemplate sql;
        ResourceFixtureController(AuthorizedTenantOperations operations,DataSource source){ this.operations=operations; sql=new JdbcTemplate(source); }
        @GetMapping(ROOT+"{tenant}/list") List<UUID> list(@PathVariable UUID tenant) { return rows(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.Kind.TAXPAYER,0); }
        @GetMapping(ROOT+"{tenant}/page") List<UUID> page(@PathVariable UUID tenant,@RequestParam int offset) { return rows(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.Kind.TAXPAYER,offset); }
        List<UUID> rows(UUID tenant,Capability cap,ResourceTarget.Kind kind,int offset) {
            return operations.execute(tenant,cap,ResourceTarget.collection(kind),scope->sql.queryForList("SELECT id FROM resource_fixture.items ORDER BY id LIMIT 10 OFFSET ?",UUID.class,offset));
        }
        @GetMapping(ROOT+"{tenant}/count") Map<String,Integer> count(@PathVariable UUID tenant){ return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->Map.of("count",sql.queryForObject("SELECT count(*) FROM resource_fixture.items",Integer.class))); }
        @GetMapping(ROOT+"{tenant}/item/{id}") Map<String,String> item(@PathVariable UUID tenant,@PathVariable UUID id){ return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),s->{ var labels=sql.queryForList("SELECT label FROM resource_fixture.items WHERE id=? AND state='ACTIVE'",String.class,id); if(labels.isEmpty())throw new ApiFailure(ApiErrorCode.NOT_FOUND); return Map.of("label",labels.getFirst()); }); }
        @PostMapping(ROOT+"{tenant}/edit/{id}") Map<String,Integer> update(@PathVariable UUID tenant,@PathVariable UUID id){ return operations.execute(tenant,Capability.TAXPAYER_EDIT,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),s->{ int n=sql.update("UPDATE resource_fixture.items SET version=version+1 WHERE id=? AND version=0 AND state='ACTIVE'",id); if(n!=1)throw new ApiFailure(ApiErrorCode.STALE_VERSION); return Map.of("changed",n); }); }
        @GetMapping(ROOT+"{tenant}/raw-write/{id}") Map<String,Integer> rawWrite(@PathVariable UUID tenant,@PathVariable UUID id){ return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->Map.of("changed",sql.update("UPDATE resource_fixture.items SET label='DEMO changed'")+sql.update("DELETE FROM resource_fixture.items"))); }
        @GetMapping(ROOT+"{tenant}/export") List<UUID> export(@PathVariable UUID tenant){ return rows(tenant,Capability.TAXPAYER_EXPORT,ResourceTarget.Kind.TAXPAYER,0); }
        @GetMapping(ROOT+"{tenant}/reports") List<UUID> reports(@PathVariable UUID tenant){ return rows(tenant,Capability.REPORT_EXPORT,ResourceTarget.Kind.REPORT,0); }
        @GetMapping(ROOT+"{tenant}/download/{id}") List<UUID> download(@PathVariable UUID tenant,@PathVariable UUID id){ return operations.execute(tenant,Capability.DOCUMENT_DOWNLOAD,ResourceTarget.item(ResourceTarget.Kind.DOCUMENT,id),s->sql.queryForList("SELECT id FROM resource_fixture.items WHERE id=?",UUID.class,id)); }
        @GetMapping(ROOT+"{tenant}/admin") String admin(@PathVariable UUID tenant){ return operations.execute(tenant,Capability.TENANT_SETTINGS_MANAGE,ResourceTarget.tenant(),s->"unused"); }
        @GetMapping(ROOT+"{tenant}/raw-invite") UUID rawInvite(@PathVariable UUID tenant){ return operations.execute(tenant,Capability.TENANT_READ,ResourceTarget.tenant(),s->{ sql.queryForObject("SELECT identity.authorize_tenant(?,true)",Object.class,tenant); return sql.queryForObject("SELECT identity.create_invitation(?,'DEMO','DEMO','ACCOUNTANT',decode(repeat('00',32),'hex'),clock_timestamp()+interval '1 hour')",UUID.class,tenant); }); }
        @GetMapping(ROOT+"{tenant}/raw-member-directory") List<UUID> rawMembers(@PathVariable UUID tenant){ return operations.execute(tenant,Capability.TENANT_READ,ResourceTarget.tenant(),s->{ sql.queryForObject("SELECT identity.authorize_tenant(?,true)",Object.class,tenant); return sql.queryForList("SELECT id FROM identity.memberships",UUID.class); }); }
        @GetMapping(ROOT+"{tenant}/retained") String retained(@PathVariable UUID tenant){ AuthorizedAccess access=operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->s); access.requireCurrent(); return "unused"; }
        @GetMapping(ROOT+"{tenant}/nested/{id}") String nested(@PathVariable UUID tenant,@PathVariable UUID id){ return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),a->"unused")); }
        @PostMapping(ROOT+"{tenant}/rollback/{id}") void rollback(@PathVariable UUID tenant,@PathVariable UUID id){ operations.execute(tenant,Capability.TAXPAYER_EDIT,ResourceTarget.item(ResourceTarget.Kind.TAXPAYER,id),s->{ sql.update("UPDATE resource_fixture.items SET version=version+1 WHERE id=?",id); throw new ApiFailure(ApiErrorCode.CONFLICT); }); }
        @PostMapping(ROOT+"{tenant}/batch/{first}/{second}") void batch(@PathVariable UUID tenant,@PathVariable UUID first,@PathVariable UUID second){ operations.execute(tenant,Capability.TAXPAYER_EDIT,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->{ if(sql.update("UPDATE resource_fixture.items SET version=version+1 WHERE id IN (?,?)",first,second)!=2)throw new ApiFailure(ApiErrorCode.NOT_FOUND); return null; }); }
        @GetMapping(ROOT+"{tenant}/hold") List<UUID> hold(@PathVariable UUID tenant){ return operations.execute(tenant,Capability.TAXPAYER_VIEW,ResourceTarget.collection(ResourceTarget.Kind.TAXPAYER),s->{ entered.countDown(); try { if(!release.await(12,TimeUnit.SECONDS))throw new ApiFailure(ApiErrorCode.CONFLICT); } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new ApiFailure(ApiErrorCode.CONFLICT);} return sql.queryForList("SELECT id FROM resource_fixture.items",UUID.class); }); }
    }
}
