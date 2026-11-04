package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Real OIDC sessions, non-superuser SQL roles and durable facts. No authentication replacement. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(60)
class TenantProvisioningTests {
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DATABASE=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        DATABASE.register(r);TLS.register(r);r.add("fiscuvia.identity.enabled",()->true);r.add("fiscuvia.identity.issuer",OIDC::issuer);
        r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");
    }
    static OidcBrowser owner,other,unassigned;static UUID ownerId,otherId,unassignedId;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.session.SessionRepository<? extends org.springframework.session.Session> sessions;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext application;
    @BeforeEach void verifiedIdentities() throws Exception {
        if(owner!=null)return;
        owner=new OidcBrowser(TLS,OIDC);other=new OidcBrowser(TLS,OIDC);unassigned=new OidcBrowser(TLS,OIDC);
        ownerId=UUID.fromString(owner.login("alice").path("userId").asText());
        otherId=UUID.fromString(other.login("bob").path("userId").asText());
        unassignedId=UUID.fromString(unassigned.login("carol").path("userId").asText());
        // Synthetic policy exercise only until #31's permanent owner policy is explicitly approved.
        admin(s->s.update("INSERT INTO access_control_private.role_capabilities VALUES('FIRM_OWNER_ADMIN','TENANT_SETTINGS_MANAGE','TENANT',true,'SYNTHETIC TEST #31') ON CONFLICT DO NOTHING"));
    }
    @AfterAll static void close(){if(owner!=null)owner.close();if(other!=null)other.close();if(unassigned!=null)unassigned.close();}
    Connection migration() throws SQLException{return DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword);}
    void admin(Consumer<JdbcTemplate> work) throws Exception {
        try(var c=migration()){c.setAutoCommit(false);work.accept(new JdbcTemplate(new SingleConnectionDataSource(c,true)));c.commit();}
    }
    UUID provision(UUID command,UUID user,String name) throws Exception {
        UUID[] result={null};admin(s->result[0]=s.queryForObject("SELECT tenant_private.provision(?,?,?,?,'contacto@example.invalid',NULL,'America/Mexico_City','es-MX')",
                UUID.class,command,user,command,name));return result[0];
    }
    UUID firm() throws Exception{return provision(UUID.randomUUID(),ownerId,"Despacho de prueba");}
    String path(UUID tenant){return "/api/v1/tenants/"+tenant+"/settings";}
    String body(long version,String name){return OidcBrowser.JSON.writeValueAsString(Map.of("expectedVersion",version,"commercialName",name,"timeZone","America/Mexico_City","locale","es-MX"));}
    HttpResponse<String> update(OidcBrowser b,UUID tenant,UUID command,String body) throws Exception {
        return b.client.send(HttpRequest.newBuilder(URI.create(TLS.origin()+path(tenant))).header("Content-Type","application/json")
                .header("Idempotency-Key",command.toString()).header("X-CSRF-TOKEN",b.csrf()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    List<Map<String,Object>> events(UUID tenant) throws Exception {
        var rows=new ArrayList<Map<String,Object>>();admin(s->{s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            rows.addAll(s.queryForList("SELECT actor_kind,actor_id,event_code,correlation_id,command_id,approval_reference FROM journal.events ORDER BY occurred_at,event_id"));});return rows;
    }
    long version(UUID tenant) throws Exception {return OidcBrowser.json(owner.get(path(tenant))).path("version").asLong();}
    List<String> directoryValues(String key) throws Exception {
        var values=new ArrayList<String>();for(var m:OidcBrowser.json(owner.get("/api/v1/me")).path("memberships"))values.add(m.path(key).asText());return values;
    }
    void status(UUID tenant,long version,String status) throws Exception {admin(s->s.queryForObject("SELECT tenant_private.change_status(?,?,?,?,?)",Long.class,tenant,UUID.randomUUID(),UUID.randomUUID(),version,status));}

    @Test void provisioningCreatesVerifiedOwnerRootProfileAuditAndOutboxAtomically() throws Exception {
        UUID tenant=firm();var response=owner.get(path(tenant));assertThat(response.statusCode()).isEqualTo(200);
        var profile=OidcBrowser.json(response);assertThat(profile.path("commercialName").asText()).isEqualTo("Despacho de prueba");
        assertThat(profile.path("status").asText()).isEqualTo("ACTIVE");assertThat(profile.path("logoReference").isNull()).isTrue();
        var rows=events(tenant);assertThat(rows).hasSize(2);assertThat(rows).extracting(r->r.get("event_code")).containsExactly("OWNER_BOOTSTRAPPED","TENANT_CREATED");
        assertThat(rows).allSatisfy(r->{assertThat(r.get("actor_kind")).isEqualTo("DATABASE_ADMIN");assertThat(r.get("actor_id")).isNull();assertThat(r.get("approval_reference")).isNotNull();});
        admin(s->{s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            assertThat(s.queryForObject("SELECT count(*) FROM journal.outbox",Integer.class)).isEqualTo(2);
            assertThat(s.queryForObject("SELECT user_id FROM identity.memberships WHERE tenant_id=?",UUID.class,tenant)).isEqualTo(ownerId);});
    }
    @Test void ownerMustHaveAnExistingVerifiedOidcIdentity() throws Exception {
        assertThatThrownBy(()->provision(UUID.randomUUID(),UUID.randomUUID(),"No permitido")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void runtimeCannotInvokePrivateProvisioningOrReadRootMetadata() throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.RUNTIME_USER,DATABASE.runtimePassword)){
            var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            for(String query:List.of("SELECT * FROM tenant_private.profiles","SELECT tenant_private.provision(NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)","SELECT tenant_private.change_status(NULL,NULL,NULL,NULL,NULL)"))
                assertThatThrownBy(()->sql.queryForList(query)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void sessionRoleCannotReadProfilesOrInvokeRuntimeFunctions() throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),"fiscuvia_sessions",DATABASE.sessionPassword)){
            var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            assertThatThrownBy(()->sql.queryForList("SELECT tenant_api.profile(NULL)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->sql.queryForList("SELECT * FROM tenant_private.admin_commands")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void anonymousAndOrdinaryMembersHaveNoProvisioningApi() throws Exception {
        try(var anonymous=new OidcBrowser(TLS,OIDC)){
            assertThat(anonymous.get(path(UUID.randomUUID())).statusCode()).isEqualTo(401);
            assertThat(anonymous.send("POST","/api/v1/tenants","{}",anonymous.csrf()).statusCode()).isIn(403,404);
            assertThat(owner.send("POST","/api/v1/tenants","{}",owner.csrf()).statusCode()).isEqualTo(403);
        }
    }
    @Test void repeatedAdministrativeCommandReturnsTheSameRootWithoutDuplicateEvents() throws Exception {
        UUID command=UUID.randomUUID();UUID tenant=provision(command,ownerId,"Mismo nombre");
        assertThat(provision(command,ownerId,"Mismo nombre")).isEqualTo(tenant);assertThat(events(tenant)).hasSize(2);
        assertThatThrownBy(()->provision(command,otherId,"Mismo nombre")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void twoFirmsMayHaveTheSameCommercialNameWithoutSharingRows() throws Exception {
        UUID a=provision(UUID.randomUUID(),ownerId,"Nombre compartido"),b=provision(UUID.randomUUID(),otherId,"Nombre compartido");
        assertThat(a).isNotEqualTo(b);assertThat(owner.get(path(a)).statusCode()).isEqualTo(200);assertThat(other.get(path(b)).statusCode()).isEqualTo(200);
        assertThat(owner.get(path(b)).statusCode()).isEqualTo(403);assertThat(other.get(path(a)).statusCode()).isEqualTo(403);
    }
    @Test void profileUpdateRecordsRealActorAndServerCorrelationWithoutPersonalMetadata() throws Exception {
        UUID tenant=firm(),command=UUID.randomUUID();var response=update(owner,tenant,command,body(0,"Nueva identidad comercial"));
        assertThat(response.statusCode()).isEqualTo(200);assertThat(OidcBrowser.json(response).path("version").asLong()).isEqualTo(1);
        assertThat(OidcBrowser.json(owner.get(path(tenant))).path("commercialName").asText()).isEqualTo("Nueva identidad comercial");
        var event=events(tenant).stream().filter(r->r.get("event_code").equals("TENANT_PROFILE_UPDATED")).findFirst().orElseThrow();
        assertThat(event.get("actor_id")).isEqualTo(ownerId);assertThat(event.get("actor_kind")).isEqualTo("USER");
        assertThat(event.get("correlation_id").toString()).isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow());
        assertThat(event.get("command_id")).isEqualTo(command);assertThat(event.values().toString()).doesNotContain("Nueva identidad","contacto@");
    }
    @Test void foreignTenantAndUnassignedIdentityCannotReadOrUpdate() throws Exception {
        UUID tenant=firm();assertThat(other.get(path(tenant)).statusCode()).isEqualTo(403);assertThat(unassigned.get(path(tenant)).statusCode()).isEqualTo(403);
        assertThat(update(other,tenant,UUID.randomUUID(),body(0,"Ataque")).statusCode()).isEqualTo(403);assertThat(version(tenant)).isZero();
    }
    @Test void rolesAndOwnerUuidsInTheBodyCannotGrantAuthority() throws Exception {
        UUID tenant=firm();String bad=body(0,"Ataque").replace("}",",\"ownerId\":\""+otherId+"\",\"role\":\"FIRM_OWNER_ADMIN\"}");
        assertThat(update(owner,tenant,UUID.randomUUID(),bad).statusCode()).isEqualTo(400);assertThat(version(tenant)).isZero();
    }
    @Test void csrfIsRequiredEvenForAnAuthenticatedOwner() throws Exception {
        UUID tenant=firm();var response=owner.client.send(HttpRequest.newBuilder(URI.create(TLS.origin()+path(tenant)))
                .header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body(0,"Sin CSRF"))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);assertThat(version(tenant)).isZero();
    }
    @Test void versionConflictDoesNotOverwriteOrAuditSuccess() throws Exception {
        UUID tenant=firm();assertThat(update(owner,tenant,UUID.randomUUID(),body(0,"Primero")).statusCode()).isEqualTo(200);
        var response=update(owner,tenant,UUID.randomUUID(),body(0,"Obsoleto"));assertThat(response.statusCode()).isEqualTo(409);
        assertThat(OidcBrowser.json(response).path("code").asText()).isEqualTo("STALE_VERSION");assertThat(events(tenant)).hasSize(3);
    }
    @Test void retriesAreDeduplicatedButAChangedPayloadIsRejected() throws Exception {
        UUID tenant=firm(),command=UUID.randomUUID();String body=body(0,"Idempotente");
        assertThat(update(owner,tenant,command,body).statusCode()).isEqualTo(200);assertThat(update(owner,tenant,command,body).statusCode()).isEqualTo(200);
        assertThat(events(tenant)).hasSize(3);assertThat(version(tenant)).isEqualTo(1);
        var response=update(owner,tenant,command,body(0,"Distinto"));assertThat(response.statusCode()).isEqualTo(409);
        assertThat(OidcBrowser.json(response).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
    @Test void validationRejectsEmptyLongAndControlBearingNamesAndInvalidFormats() throws Exception {
        UUID tenant=firm();
        for(String name:List.of("","x".repeat(161),"DEMO\nSECRETO"))assertThat(update(owner,tenant,UUID.randomUUID(),body(0,name)).statusCode()).isEqualTo(422);
        for(String suffix:List.of(",\"contactEmail\":\"invalido\"",",\"phone\":\"abc\""))
            assertThat(update(owner,tenant,UUID.randomUUID(),body(0,"Válido").replace("}",suffix+"}")).statusCode()).isEqualTo(422);
        assertThat(update(owner,tenant,UUID.randomUUID(),body(0,"Válido").replace("America/Mexico_City","UTC")).statusCode()).isEqualTo(422);
        assertThat(version(tenant)).isZero();
    }
    @Test void suspensionBlocksProtectedAccessButPreservesOwnDirectoryAndHistory() throws Exception {
        UUID tenant=firm();status(tenant,0,"SUSPENDED");assertThat(owner.get(path(tenant)).statusCode()).isEqualTo(403);
        assertThat(owner.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+tenant+"\"}",owner.csrf()).statusCode()).isEqualTo(403);
        assertThat(directoryValues("tenantStatus")).contains("SUSPENDED");
        assertThat(events(tenant)).hasSize(3);status(tenant,1,"ACTIVE");assertThat(owner.get(path(tenant)).statusCode()).isEqualTo(200);assertThat(events(tenant)).hasSize(4);
    }
    @Test void runtimeCannotReactivateAClockOrUuidSelectedTenant() throws Exception {
        UUID tenant=firm();status(tenant,0,"SUSPENDED");
        assertThat(owner.send("POST","/api/v1/tenants/"+tenant+"/status","{\"status\":\"ACTIVE\"}",owner.csrf()).statusCode()).isEqualTo(403);
        assertThat(update(owner,tenant,UUID.randomUUID(),body(1,"No permitido")).statusCode()).isEqualTo(403);
    }
    @Test void runtimeSqlCannotUseProfilesWithoutAnAuthorizedOperationOrByForgingGucs() throws Exception {
        UUID tenant=firm();try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.RUNTIME_USER,DATABASE.runtimePassword)){
            c.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            sql.queryForObject("SELECT set_config('app.current_tenant_id',?,true)",String.class,tenant.toString());
            assertThat(sql.queryForList("SELECT * FROM identity.own_firms()")).isEmpty();
            assertThatThrownBy(()->sql.queryForList("SELECT * FROM tenant_api.profile(?)",tenant)).isInstanceOf(org.springframework.dao.DataAccessException.class);c.rollback();
        }
    }
    @Test void administrativeRollbackLeavesNeitherTenantMembershipNorJournal() throws Exception {
        UUID[] tenant={null};try(var c=migration()){c.setAutoCommit(false);var s=new JdbcTemplate(new SingleConnectionDataSource(c,true));
            tenant[0]=s.queryForObject("SELECT tenant_private.provision(?,?,?,'Rollback',NULL,NULL,'America/Mexico_City','es-MX')",UUID.class,UUID.randomUUID(),ownerId,UUID.randomUUID());c.rollback();}
        assertThat(owner.get(path(tenant[0])).statusCode()).isEqualTo(403);
        admin(s->assertThat(s.queryForObject("SELECT count(*) FROM tenant_private.profiles WHERE tenant_id=?",Integer.class,tenant[0])).isZero());
    }
    @Test void concurrentAdministrativeReplayCreatesOneFirm() throws Exception {
        UUID command=UUID.randomUUID();try(var workers=Executors.newFixedThreadPool(2)){
            var start=new CyclicBarrier(2);Callable<UUID> work=()->{start.await(10,TimeUnit.SECONDS);return provision(command,ownerId,"Concurrente");};
            var one=workers.submit(work);var two=workers.submit(work);UUID tenant=one.get(20,TimeUnit.SECONDS);assertThat(two.get(20,TimeUnit.SECONDS)).isEqualTo(tenant);assertThat(events(tenant)).hasSize(2);
        }
    }
    @Test void revokedMembershipLosesDirectoryAndProtectedProfileImmediately() throws Exception {
        UUID tenant=firm();admin(s->{s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            s.update("UPDATE identity.memberships SET status='REVOKED' WHERE tenant_id=? AND user_id=?",tenant,ownerId);});
        assertThat(owner.get(path(tenant)).statusCode()).isEqualTo(403);
        assertThat(directoryValues("tenantId")).doesNotContain(tenant.toString());
    }
    @Test void eachNonOwnerRoleCanReadButCannotManageEvenWhenClaimsSuggestOtherwise() throws Exception {
        for(String role:List.of("FIRM_SUPERVISOR","ACCOUNTANT","ASSISTANT")) {
            UUID tenant=firm(),member=UUID.randomUUID();admin(s->{s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
                s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,member,otherId);
                s.update("INSERT INTO identity.membership_roles VALUES(?,?,?)",tenant,member,role);});
            var get=other.get(path(tenant));assertThat(get.statusCode()).isEqualTo(200);assertThat(OidcBrowser.json(get).path("canManage").asBoolean()).isFalse();
            assertThat(update(other,tenant,UUID.randomUUID(),body(0,"No permitido")).statusCode()).isEqualTo(403);assertThat(version(tenant)).isZero();
        }
    }
    @Test void ownerWithoutVerifiedRecentMfaCannotManage() throws Exception {
        UUID tenant=firm();
        String sessionId=new String(Base64.getDecoder().decode(owner.sessionCookie()),java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.session.Session session=sessions.findById(sessionId);
        org.springframework.security.core.context.SecurityContext context=session.getAttribute("SPRING_SECURITY_CONTEXT");
        Object original=context.getAuthentication().getPrincipal();
        var type=original.getClass();var fields=type.getRecordComponents();var types=Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);
        for(long mfa:List.of(0L,java.time.Instant.now().getEpochSecond()-601)) {
            Object[] values=new Object[fields.length];for(int i=0;i<fields.length;i++){var accessor=fields[i].getAccessor();accessor.setAccessible(true);values[i]=fields[i].getName().equals("mfaAt")?mfa:accessor.invoke(original);}
            Object replacement=constructor.newInstance(values);
            var token=new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken((org.springframework.security.oauth2.core.user.OAuth2User)replacement,
                context.getAuthentication().getAuthorities(),"local");
            context.setAuthentication(token);session.setAttribute("SPRING_SECURITY_CONTEXT",context);saveSession(session);
            try {assertThat(OidcBrowser.json(owner.get(path(tenant))).path("canManage").asBoolean()).isFalse();assertThat(update(owner,tenant,UUID.randomUUID(),body(0,"MFA insuficiente")).statusCode()).isEqualTo(403);}
            finally {context.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken((org.springframework.security.oauth2.core.user.OAuth2User)original,token.getAuthorities(),"local"));session.setAttribute("SPRING_SECURITY_CONTEXT",context);saveSession(session);}
        }
        assertThat(version(tenant)).isZero();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) void saveSession(org.springframework.session.Session session){((org.springframework.session.SessionRepository)sessions).save(session);}
    @Test void journalFailureRollsBackProfileAndDoesNotLeaveACommandReceipt() throws Exception { failedSink("events"); }
    @Test void outboxFailureRollsBackProfileAndJournalTogether() throws Exception { failedSink("outbox"); }
    void failedSink(String table) throws Exception {
        UUID tenant=firm(),command=UUID.randomUUID();
        admin(s->{s.execute("CREATE FUNCTION tenant_private.fail_sink() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC_SINK_FAILURE'; END $$");
            s.execute("CREATE TRIGGER fail_31 BEFORE INSERT ON journal."+table+" FOR EACH ROW EXECUTE FUNCTION tenant_private.fail_sink()");});
        try {var response=update(owner,tenant,command,body(0,"No se confirma"));assertThat(response.statusCode()).isEqualTo(500);assertThat(response.body()).doesNotContain("SYNTHETIC_SINK_FAILURE","No se confirma","SQL");}
        finally {admin(s->{s.execute("DROP TRIGGER fail_31 ON journal."+table);s.execute("DROP FUNCTION tenant_private.fail_sink()");});}
        assertThat(version(tenant)).isZero();assertThat(events(tenant)).hasSize(2);
        assertThat(update(owner,tenant,command,body(0,"No se confirma")).statusCode()).isEqualTo(200);assertThat(events(tenant)).hasSize(3);
    }
    @Test void provisioningSinkFailureLeavesNoRootOwnerOrAdministrativeReceipt() throws Exception {
        UUID command=UUID.randomUUID();int[] before={0};admin(s->before[0]=s.queryForObject("SELECT count(*) FROM tenant_private.profiles",Integer.class));
        admin(s->{s.execute("CREATE FUNCTION tenant_private.fail_create() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC_CREATE_FAILURE'; END $$");
            s.execute("CREATE TRIGGER fail_31 BEFORE INSERT ON journal.outbox FOR EACH ROW EXECUTE FUNCTION tenant_private.fail_create()");});
        try {assertThatThrownBy(()->provision(command,ownerId,"No creado")).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {admin(s->{s.execute("DROP TRIGGER fail_31 ON journal.outbox");s.execute("DROP FUNCTION tenant_private.fail_create()");});}
        admin(s->{assertThat(s.queryForObject("SELECT count(*) FROM tenant_private.profiles",Integer.class)).isEqualTo(before[0]);assertThat(s.queryForObject("SELECT count(*) FROM tenant_private.admin_commands WHERE command_id=?",Integer.class,command)).isZero();});
        assertThat(events(provision(command,ownerId,"No creado"))).hasSize(2);
    }
    @Test void concurrentUpdatesWithTheSameExpectedVersionHaveOnlyOneWinner() throws Exception {
        UUID tenant=firm();try(var workers=Executors.newFixedThreadPool(2)) {
            var start=new CyclicBarrier(2);Callable<Integer> work=()->{start.await(10,TimeUnit.SECONDS);return update(owner,tenant,UUID.randomUUID(),body(0,"Actualización concurrente")).statusCode();};
            var one=workers.submit(work);var two=workers.submit(work);assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(version(tenant)).isEqualTo(1);assertThat(events(tenant)).hasSize(3);
    }
    @Test void suspensionWaitsForAuthorizedOperationAndWinsForTheNextOperation() throws Exception {
        UUID tenant=firm();try(var locked=DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.RUNTIME_USER,DATABASE.runtimePassword);
                var workers=Executors.newSingleThreadExecutor()) {
            locked.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(locked,true));
            String tx=sql.queryForObject("SELECT pg_current_xact_id()::text",String.class);long mfa=java.time.Instant.now().getEpochSecond();
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(HexFormat.of().parseHex(DATABASE.actorKey),"HmacSHA256"));
            String payload=OIDC.issuer()+"\n"+KeycloakTestProvider.subject(owner.username)+"\n"+tx+"\n"+mfa;
            sql.queryForList("SELECT * FROM identity.bind_actor(?,?,?,?)",OIDC.issuer(),KeycloakTestProvider.subject(owner.username).toString(),mfa,HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            assertThat(sql.queryForObject("SELECT access_control.authorize_operation(?,'TENANT_READ','TENANT',NULL)",String.class,tenant)).isEqualTo("AUTHORIZED");
            int pid=sql.queryForObject("SELECT pg_backend_pid()",Integer.class);
            var suspend=workers.submit(()->{status(tenant,0,"SUSPENDED");return true;});
            ThreatHarness.until(()->{try(var c=migration();var s=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)))")){s.setInt(1,pid);try(var r=s.executeQuery()){r.next();return r.getBoolean(1);}}catch(Exception e){throw new IllegalStateException("Synthetic lock inspection failed");}});
            assertThat(suspend.isDone()).isFalse();locked.commit();assertThat(suspend.get(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(owner.get(path(tenant)).statusCode()).isEqualTo(403);
    }
    @Test void profileGuardDetectsAnExcessiveRuntimeGrant() throws Exception {
        var guard=application.getBean("verifyProfilePrivileges",org.springframework.beans.factory.InitializingBean.class);
        admin(s->s.execute("GRANT SELECT ON tenant_private.profiles TO fiscuvia_runtime"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(com.fiscuvia.platform.StartupGuardFailure.class);}
        finally {admin(s->s.execute("REVOKE SELECT ON tenant_private.profiles FROM fiscuvia_runtime"));}
        guard.afterPropertiesSet();
    }
    @Test void profileGuardDetectsRemovalOfForceRls() throws Exception {
        var guard=application.getBean("verifyProfilePrivileges",org.springframework.beans.factory.InitializingBean.class);
        admin(s->s.execute("ALTER TABLE tenant_private.profiles NO FORCE ROW LEVEL SECURITY"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(com.fiscuvia.platform.StartupGuardFailure.class);}
        finally {admin(s->s.execute("ALTER TABLE tenant_private.profiles FORCE ROW LEVEL SECURITY"));}
        guard.afterPropertiesSet();
    }
    @Test void profileGuardDetectsRuntimeExecutionOfAnAdministrativeFunction() throws Exception {
        var guard=application.getBean("verifyProfilePrivileges",org.springframework.beans.factory.InitializingBean.class);
        admin(s->s.execute("GRANT EXECUTE ON FUNCTION tenant_private.change_status(uuid,uuid,uuid,bigint,text) TO fiscuvia_runtime"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(com.fiscuvia.platform.StartupGuardFailure.class);}
        finally {admin(s->s.execute("REVOKE EXECUTE ON FUNCTION tenant_private.change_status(uuid,uuid,uuid,bigint,text) FROM fiscuvia_runtime"));}
        guard.afterPropertiesSet();
    }
    @Test void ownDirectoryOracleDetectsRemovalOfTheActorFence() throws Exception {
        UUID foreign=provision(UUID.randomUUID(),otherId,"Ajeno DEMO");assertThat(directoryValues("tenantId")).doesNotContain(foreign.toString());
        String[] definition={null};admin(s->definition[0]=s.queryForObject("SELECT pg_get_functiondef('identity.own_firms()'::regprocedure)",String.class));
        String weakened=definition[0].replace("m.user_id=identity.current_actor_id() AND ","");assertThat(weakened).isNotEqualTo(definition[0]);
        var guard=application.getBean("verifyProfilePrivileges",org.springframework.beans.factory.InitializingBean.class);
        guard.afterPropertiesSet();
        try {
            admin(s->s.execute(weakened));assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(com.fiscuvia.platform.StartupGuardFailure.class);
            // Membership RLS is an independent backstop; removing one fence must not be relabeled a leak.
            assertThat(directoryValues("tenantId")).doesNotContain(foreign.toString());
        }
        finally {admin(s->s.execute(definition[0]));}
        guard.afterPropertiesSet();
        assertThat(directoryValues("tenantId")).doesNotContain(foreign.toString());
    }
    @Test void configurationCanariesNeverEnterLogsJournalOrOutbox(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        UUID tenant=firm();String name="DEMO-RFC-PRIVATE-NAME-CANARY",email="private-contact-canary@example.invalid",phone="+52 000 000 0000";
        String request=OidcBrowser.JSON.writeValueAsString(Map.of("expectedVersion",0,"commercialName",name,"contactEmail",email,"phone",phone,"timeZone","America/Mexico_City","locale","es-MX"));
        assertThat(update(owner,tenant,UUID.randomUUID(),request).statusCode()).isEqualTo(200);
        assertThat(OidcBrowser.json(owner.get(path(tenant))).path("contactEmail").asText()).isEqualTo(email);
        assertThat(output.getAll()).doesNotContain(name,email,phone);
        assertThat(DATABASE.getLogs()).doesNotContain(name,email,phone);
        admin(s->{s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
            assertThat(s.queryForList("SELECT payload::text FROM journal.outbox",String.class)).allSatisfy(payload->assertThat(payload).doesNotContain(name,email,phone));});
        assertThat(other.get(path(tenant)).body()).doesNotContain(name,email,phone);
    }
}
