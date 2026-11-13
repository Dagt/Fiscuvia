package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Actual V12 policies, OIDC/CSRF, runtime role, PostgreSQL and real transactional sinks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @Timeout(90)
class TaxpayerTests {
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DB=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){DB.register(r);TLS.register(r);
        r.add("fiscuvia.identity.enabled",()->true);r.add("fiscuvia.identity.issuer",OIDC::issuer);
        r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");}
    static OidcBrowser owner,person,other;static UUID ownerId,personId,otherId;
    static final List<Map<String,Object>> EVIDENCE=Collections.synchronizedList(new ArrayList<>());
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext application;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.session.SessionRepository<? extends org.springframework.session.Session> sessions;
    @BeforeEach void actors() throws Exception {if(owner!=null&&owner.get("/api/v1/me").statusCode()==200)return;
        if(owner!=null){owner.close();person.close();other.close();}
        owner=new OidcBrowser(TLS,OIDC);person=new OidcBrowser(TLS,OIDC);other=new OidcBrowser(TLS,OIDC);
        ownerId=UUID.fromString(owner.login("alice").path("userId").asText());personId=UUID.fromString(person.login("bob").path("userId").asText());otherId=UUID.fromString(other.login("carol").path("userId").asText());}
    @AfterAll static void close() throws Exception {java.nio.file.Files.writeString(java.nio.file.Path.of("target/taxpayer-contract-responses.json"),OidcBrowser.JSON.writeValueAsString(EVIDENCE));if(owner!=null)owner.close();if(person!=null)person.close();if(other!=null)other.close();}
    static HttpResponse<String> evidence(String method,String path,HttpResponse<String> response) {
        String route=path.split("\\?")[0].replaceAll("/tenants/[^/]+/taxpayers","/tenants/{tenantId}/taxpayers").replaceAll("/taxpayers/[^/]+","/taxpayers/{taxpayerId}");
        if(path.contains("/taxpayers"))EVIDENCE.add(Map.of("method",method.toLowerCase(Locale.ROOT),"route",route,"status",response.statusCode(),"contentType",response.headers().firstValue("Content-Type").orElse(""),"correlationId",response.headers().firstValue("X-Correlation-Id").orElse(""),"cacheControl",response.headers().firstValue("Cache-Control").orElse(""),"body",OidcBrowser.json(response)));
        return response;
    }
    Connection migration() throws Exception {return DriverManager.getConnection(DB.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DB.migrationPassword);}
    void admin(UUID tenant,Consumer<JdbcTemplate> work) throws Exception {try(var c=migration()){c.setAutoCommit(false);var s=new JdbcTemplate(new SingleConnectionDataSource(c,true));if(tenant!=null)s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);work.accept(s);c.commit();}}
    UUID firm() throws Exception {return firm(ownerId,20);}
    UUID firm(UUID user,long capacity) throws Exception {UUID[] id={null};admin(null,s->id[0]=s.queryForObject("SELECT tenant_private.provision(?,?,?,'C01 DEMO',NULL,NULL,'America/Mexico_City','es-MX')",UUID.class,UUID.randomUUID(),user,UUID.randomUUID()));admin(id[0],s->QuotaFixtures.seed(s,id[0]));if(capacity>=0)quota(id[0],capacity);return id[0];}
    void quota(UUID t,long n) throws Exception {admin(null,s->{Long v=s.queryForObject("SELECT coalesce((SELECT version FROM entitlement_private.limits WHERE tenant_id=? AND resource='TAXPAYERS'),0)",Long.class,t);
        s.queryForObject("SELECT entitlement_private.assign_limit(?,?,?,'TAXPAYERS',?,?,NULL)",Long.class,t,UUID.randomUUID(),UUID.randomUUID(),n,v);});}
    UUID member(UUID t,UUID user,String role) throws Exception {UUID id=UUID.randomUUID();admin(t,s->{s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",t,id,user);s.update("INSERT INTO identity.membership_roles VALUES(?,?,?)",t,id,role);});return id;}
    String path(UUID t){return "/api/v1/tenants/"+t+"/taxpayers";}
    Map<String,Object> profile(String name,List<?> regimes){var b=new LinkedHashMap<String,Object>();b.put("legalName",name);b.put("tradeName",null);b.put("regimes",regimes);return b;}
    Map<String,Object> create(String rfc,String type){var b=profile("Cliente DEMO",List.of());b.put("rfc",rfc);b.put("personType",type);return b;}
    Map<String,Object> edit(long version,String name,List<?> regimes){var b=profile(name,regimes);b.put("expectedVersion",version);return b;}
    Map<String,Object> regime(String code,String from,String to){var b=new LinkedHashMap<String,Object>();b.put("code",code);b.put("validFrom",from);b.put("validTo",to);return b;}
    HttpResponse<String> request(OidcBrowser b,String method,String path,UUID command,Map<String,?> body) throws Exception {return raw(b,method,path,command,OidcBrowser.JSON.writeValueAsString(body),true);}
    HttpResponse<String> raw(OidcBrowser b,String method,String path,UUID command,String body,boolean csrf) throws Exception {
        var r=HttpRequest.newBuilder(URI.create(TLS.origin()+path)).timeout(java.time.Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(command!=null)r.header("Idempotency-Key",command.toString());if(csrf)r.header("X-CSRF-TOKEN",b.csrf());
        return evidence(method,path,b.client.send(r.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()));
    }
    UUID created(UUID t,String rfc) throws Exception {var r=request(owner,"POST",path(t),UUID.randomUUID(),create(rfc,"LEGAL_ENTITY"));assertThat(r.statusCode()).withFailMessage("Create failed: %s",r.body()).isEqualTo(201);return UUID.fromString(OidcBrowser.json(r).path("targetId").asText());}
    long count(UUID t,String table) throws Exception {long[] n={0};admin(t,s->n[0]=s.queryForObject("SELECT count(*) FROM "+table,Long.class));return n[0];}
    tools.jackson.databind.JsonNode get(UUID t,UUID id) throws Exception {var r=evidence("GET",path(t)+"/"+id,owner.get(path(t)+"/"+id));assertThat(r.statusCode()).withFailMessage("Get failed: %s",r.body()).isEqualTo(200);return OidcBrowser.json(r);}
    HttpResponse<String> archive(UUID t,UUID id,long v) throws Exception {return request(owner,"POST",path(t)+"/"+id+"/archivals",UUID.randomUUID(),Map.of("expectedVersion",v,"reasonCode","SERVICE_ENDED"));}
    HttpResponse<String> restore(UUID t,UUID id,long v) throws Exception {return request(owner,"POST",path(t)+"/"+id+"/restorations",UUID.randomUUID(),Map.of("expectedVersion",v,"reasonCode","SERVICE_RESUMED"));}
    @Test void createsLegalEntityWithRealAtomicBootstrapAndPrivateHistory() throws Exception {
        UUID t=firm();long before=count(t,"journal.events");UUID id=created(t,"DEM000101001");var value=get(t,id);
        assertThat(value.path("rfc").asText()).isEqualTo("DEM000101001");assertThat(value.path("status").asText()).isEqualTo("ACTIVE");assertThat(value.path("version").asLong()).isZero();
        assertThat(value.path("rfcValidation").asText()).isEqualTo("RFC_LOCAL_STRUCTURE_V1");assertThat(value.path("officialVerification").asText()).isEqualTo("NOT_PERFORMED");
        assertThat(count(t,"taxpayers.taxpayer_revisions")).isEqualTo(1);assertThat(count(t,"taxpayers.taxpayer_assignments")).isEqualTo(1);
        assertThat(count(t,"access_control.resource_grants")).isEqualTo(4);assertThat(count(t,"taxpayer_private.grant_reviews")).isEqualTo(3);
        assertThat(count(t,"journal.events")).isEqualTo(before+1);assertThat(count(t,"journal.outbox")).isEqualTo(before+1);
        admin(t,s->{assertThat(s.queryForObject("SELECT count(*) FROM access_control_private.role_capabilities WHERE capability='TAXPAYER_ASSIGN'",Integer.class)).isZero();
            assertThat(s.queryForObject("SELECT count(*) FROM taxpayers.taxpayer_assignments a JOIN identity.memberships m ON m.tenant_id=a.tenant_id AND m.id=a.membership_id WHERE m.user_id=? AND a.taxpayer_id=?",Integer.class,ownerId,id)).isEqualTo(1);});
    }
    @Test void createNormalizesIndividualAndDoesNotClaimOfficialVerification() throws Exception {
        UUID t=firm();var r=request(owner,"POST",path(t),UUID.randomUUID(),create("  demo000229ab1  ","INDIVIDUAL"));assertThat(r.statusCode()).isEqualTo(201);
        var value=get(t,UUID.fromString(OidcBrowser.json(r).path("targetId").asText()));assertThat(value.path("rfc").asText()).isEqualTo("DEMO000229AB1");
    }
    @Test void sameRfcAcrossTenantsRemainsIsolatedAndCannotBeEnumerated() throws Exception {
        UUID a=firm(),b=firm(otherId,20),id=created(a,"DEM000101001");var r=request(other,"POST",path(b),UUID.randomUUID(),create("DEM000101001","LEGAL_ENTITY"));assertThat(r.statusCode()).isEqualTo(201);
        assertThat(owner.get(path(b)).statusCode()).isEqualTo(403);assertThat(owner.get(path(b)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(other.get(path(b)+"/"+id).statusCode()).isEqualTo(404);
    }
    @Test void archiveRestorePreserveIdentityHistoryAndQuota() throws Exception {
        UUID t=firm(ownerId,1),id=created(t,"DEM000101001");assertThat(archive(t,id,0).statusCode()).isEqualTo(200);
        UUID another=created(t,"DEM000101002");assertThat(restore(t,id,1).statusCode()).isEqualTo(409);assertThat(archive(t,another,0).statusCode()).isEqualTo(200);
        assertThat(restore(t,id,1).statusCode()).isEqualTo(200);assertThat(get(t,id).path("version").asLong()).isEqualTo(2);
        assertThat(count(t,"taxpayers.taxpayer_revisions")).isEqualTo(5);assertThat(count(t,"taxpayers.taxpayer_assignments")).isEqualTo(2);
    }
    @Test void editsProfileWithMultipleManualRegimesAndRetainsOldSnapshot() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");var regimes=List.of(regime("000",null,null),regime("001","2026-01-01",null));
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"Nombre nuevo DEMO",regimes)).statusCode()).isEqualTo(200);
        var value=get(t,id);assertThat(value.path("regimes").size()).isEqualTo(2);assertThat(value.toString()).contains("MANUAL","PENDING_REVIEW");
        admin(t,s->assertThat(s.queryForObject("SELECT legal_name FROM taxpayers.taxpayer_revisions WHERE taxpayer_id=? AND version=0",String.class,id)).isEqualTo("Cliente DEMO"));
    }
    @Test void duplicateRfcIncludesArchivedAndReturnsNoExistingRecordData() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");assertThat(archive(t,id,0).statusCode()).isEqualTo(200);
        var r=request(owner,"POST",path(t),UUID.randomUUID(),create("dem000101001","LEGAL_ENTITY"));assertThat(r.statusCode()).isEqualTo(409);
        assertThat(r.body()).doesNotContain(id.toString(),"Cliente DEMO","DEM000101001","ARCHIVED","SQLException");
    }
    @Test void exactReplayReturnsOriginalReceiptWithoutNewEventVersionOrQuota() throws Exception {
        UUID t=firm(ownerId,1),cmd=UUID.randomUUID();var body=create("DEM000101001","LEGAL_ENTITY");
        var first=request(owner,"POST",path(t),cmd,body);assertThat(first.statusCode()).isEqualTo(201);
        long events=count(t,"journal.events");var again=request(owner,"POST",path(t),cmd,body);assertThat(again.statusCode()).isEqualTo(200);assertThat(OidcBrowser.json(again).path("replayed").asBoolean()).isTrue();
        assertThat(OidcBrowser.json(again).path("targetId")).isEqualTo(OidcBrowser.json(first).path("targetId"));assertThat(count(t,"journal.events")).isEqualTo(events);
        body.put("legalName","Otro nombre");assertThat(request(owner,"POST",path(t),cmd,body).statusCode()).isEqualTo(409);
    }
    @Test void listHasStablePaginationFiltersLiteralSearchAndNoGlobalTotal() throws Exception {
        UUID t=firm();created(t,"DEM000101001");created(t,"DEM000101002");
        var first=OidcBrowser.json(owner.get(path(t)+"?size=1&sort=legalName,asc"));var second=OidcBrowser.json(owner.get(path(t)+"?size=1&page=1&sort=legalName,asc"));
        assertThat(first.path("hasNext").asBoolean()).isTrue();assertThat(second.path("hasNext").asBoolean()).isFalse();assertThat(first.path("items").get(0).path("id")).isNotEqualTo(second.path("items").get(0).path("id"));
        assertThat(first.has("total")).isFalse();assertThat(OidcBrowser.json(owner.get(path(t)+"?search=%25")).path("items").size()).isZero();
        assertThat(OidcBrowser.json(owner.get(path(t)+"?search=000101001")).path("items").size()).isEqualTo(1);
    }

    void grant(UUID t,UUID m,UUID id,String... caps) throws Exception {for(String cap:caps)admin(t,s->TaxpayerFixtures.grant(s,t,m,id,cap,"TAXPAYER"));}
    HttpResponse<String> team(UUID t,UUID m,long v,String kind,String value) throws Exception {
        return request(owner,"POST","/api/v1/tenants/"+t+"/team/members/"+m+"/"+kind,UUID.randomUUID(),Map.of("expectedVersion",v,kind.equals("role")?"role":"status",value));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"FIRM_SUPERVISOR","ACCOUNTANT"})
    void resourceGrantAndReviewAreBothRequiredForAllowedRoles(String role) throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,role);
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(OidcBrowser.json(person.get(path(t))).path("items").size()).isZero();
        grant(t,m,id,"TAXPAYER_VIEW","TAXPAYER_EDIT");
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(200);
        assertThat(request(person,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"Contador DEMO",List.of())).statusCode()).isEqualTo(200);
        assertThat(request(person,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(403);
        assertThat(request(person,"POST",path(t)+"/"+id+"/archivals",UUID.randomUUID(),Map.of("expectedVersion",1,"reasonCode","OTHER")).statusCode()).isEqualTo(404);
    }
    @Test void ownerHasNoImplicitPortfolioAndHistoricalGrantsAreInsufficient() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");
        admin(t,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE resource_id=? AND capability='TAXPAYER_VIEW'",id));
        assertThat(owner.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(OidcBrowser.json(owner.get(path(t))).path("items").size()).isZero();
        // A create replay cannot renew a review, even though creation itself remains authorized.
        assertThat(count(t,"taxpayer_private.grant_reviews")).isEqualTo(3);
    }
    @Test void assistantDeniedEvenWithSyntheticExactGrantsAndReviews() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,"ASSISTANT");grant(t,m,id,"TAXPAYER_VIEW","TAXPAYER_EDIT");
        assertThat(person.get(path(t)).statusCode()).isEqualTo(403);assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(request(person,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"DEMO",List.of())).statusCode()).isEqualTo(404);
    }
    @Test void suspendReactivateAndRoleCycleNeverRenewHistoricalFiscalReview() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,"ACCOUNTANT");grant(t,m,id,"TAXPAYER_VIEW","TAXPAYER_EDIT");
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(200);
        assertThat(team(t,m,0,"status","SUSPENDED").statusCode()).isEqualTo(200);
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(team(t,m,1,"status","ACTIVE").statusCode()).isEqualTo(200);
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        assertThat(team(t,m,2,"role","FIRM_SUPERVISOR").statusCode()).isEqualTo(200);
        assertThat(team(t,m,3,"role","ACCOUNTANT").statusCode()).isEqualTo(200);
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        admin(t,s->{assertThat(s.queryForObject("SELECT version FROM identity.memberships WHERE id=?",Long.class,m)).isEqualTo(4);
            assertThat(s.queryForObject("SELECT count(*) FROM access_control.resource_grants WHERE membership_id=? AND active",Integer.class,m)).isEqualTo(2);
            assertThat(s.queryForObject("SELECT count(*) FROM taxpayer_private.grant_reviews WHERE membership_id=?",Integer.class,m)).isEqualTo(2);});
    }
    @Test void replayAfterRoleCycleDoesNotRecoverAccessOrDuplicateEvents() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,"ACCOUNTANT"),cmd=UUID.randomUUID();grant(t,m,id,"TAXPAYER_EDIT");
        var b=edit(0,"Cambio DEMO",List.of());assertThat(request(person,"PATCH",path(t)+"/"+id,cmd,b).statusCode()).isEqualTo(200);
        assertThat(team(t,m,0,"role","FIRM_SUPERVISOR").statusCode()).isEqualTo(200);assertThat(team(t,m,1,"role","ACCOUNTANT").statusCode()).isEqualTo(200);
        long n=count(t,"journal.events");assertThat(request(person,"PATCH",path(t)+"/"+id,cmd,b).statusCode()).isEqualTo(404);
        assertThat(count(t,"journal.events")).isEqualTo(n);assertThat(get(t,id).path("version").asLong()).isEqualTo(1);
    }
    @Test void revokedMemberAndSuspendedTenantAreDenied() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,"ACCOUNTANT");grant(t,m,id,"TAXPAYER_VIEW");
        assertThat(team(t,m,0,"status","REVOKED").statusCode()).isEqualTo(200);assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
        admin(t,s->s.update("UPDATE tenant_private.profiles SET status='SUSPENDED' WHERE tenant_id=?",t));
        assertThat(owner.get(path(t)+"/"+id).statusCode()).isEqualTo(404);assertThat(request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(403);
    }
    @Test void disabledIdentityDeniesFiscalAccess() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");admin(null,s->s.update("UPDATE identity_private.users SET status='DISABLED' WHERE id=?",ownerId));
        try {assertThat(owner.get(path(t)+"/"+id).statusCode()).isIn(401,404);}
        finally {admin(null,s->s.update("UPDATE identity_private.users SET status='ACTIVE' WHERE id=?",ownerId));}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(longs={0,1})
    void absentAndExpiredMfaDenyCreateArchiveRestoreButNotRead(long mfa) throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");Runnable reset=ThreatHarness.principalField(sessions,new ThreatHarness.Actor("alice",ownerId,owner),"mfaAt",mfa);
        try {assertThat(request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(403);
            assertThat(archive(t,id,0).statusCode()).isEqualTo(404);assertThat(restore(t,id,0).statusCode()).isEqualTo(404);assertThat(owner.get(path(t)+"/"+id).statusCode()).isEqualTo(200);}
        finally {reset.run();}
    }
    @Test void latestDisableReviewOverridesHistoricalEnable() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");admin(t,s->s.update("INSERT INTO taxpayer_private.grant_reviews SELECT tenant_id,membership_id,resource_kind,capability,resource_id,2,'DISABLE',reviewed_membership_version,reviewed_role_code,reviewed_by_membership_id,clock_timestamp(),(SELECT command_id FROM journal.events WHERE event_code='TENANT_CREATED' LIMIT 1) FROM taxpayer_private.grant_reviews WHERE resource_id=? AND capability='TAXPAYER_VIEW'",id));
        // The command reference is deliberately different for an append-only review.
        assertThat(owner.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
    }
    @Test void unknownImmutableFieldsMalformedBodiesAndBodyLimitsFailSafely() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");
        for(String field:List.of("id","tenantId","rfc","personType","status","version","createdAt","source","grants")) {
            var b=edit(0,"Nombre DEMO",List.of());b.put(field,"SENSITIVE_CANARY_34");var r=request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),b);
            assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).doesNotContain("SENSITIVE_CANARY_34");
        }
        assertThat(raw(owner,"POST",path(t),UUID.randomUUID(),"{",true).statusCode()).isEqualTo(400);
        assertThat(raw(owner,"POST",path(t),UUID.randomUUID()," ".repeat(65537),true).statusCode()).isEqualTo(413);
        assertThat(request(owner,"POST",path(t),null,create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(400);
        assertThat(raw(owner,"POST",path(t),UUID.randomUUID(),"{}",false).statusCode()).isEqualTo(403);
        try(var anonymous=new OidcBrowser(TLS,OIDC)){assertThat(anonymous.get(path(t)).statusCode()).isEqualTo(401);}
        assertThat(get(t,id).path("version").asLong()).isZero();
    }
    @Test void rejectsInvalidRegimesDatesOverlapsAndUnsafeVersions() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");
        for(var r:List.of(List.of(regime("12",null,null)),List.of(regime("001",null,"2026-01-01")),
            List.of(regime("001","2026-01-01","2026-01-01")),List.of(regime("001",null,null),regime("001",null,null)),
            List.of(regime("001","2026-01-01","2026-03-01"),regime("001","2026-02-01",null)))) {
            assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"DEMO",r)).statusCode()).isEqualTo(422);
        }
        for(String number:List.of("-1","9007199254740992","0.5","\"0\"","null"))assertThat(raw(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),"{\"expectedVersion\":"+number+",\"legalName\":\"DEMO\",\"tradeName\":null,\"regimes\":[]}",true).statusCode()).isIn(400,422);
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"DEMO",List.of(regime("001","2026-01-01","2026-02-01"),regime("001","2026-02-01",null)))).statusCode()).isEqualTo(200);
    }
    @Test void staleVersionStateTransitionsAndNoOpsHaveNoSideEffects() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");long n=count(t,"journal.events");
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"Cliente DEMO",List.of())).statusCode()).isEqualTo(409);
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(1,"DEMO",List.of())).statusCode()).isEqualTo(409);
        assertThat(restore(t,id,0).statusCode()).isEqualTo(409);assertThat(count(t,"journal.events")).isEqualTo(n);
        assertThat(archive(t,id,0).statusCode()).isEqualTo(200);assertThat(archive(t,id,1).statusCode()).isEqualTo(409);
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(1,"DEMO",List.of())).statusCode()).isEqualTo(409);
    }
    @Test void invalidOrMissingQuotaDeniesOnlyCapacityIncreases() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");admin(t,s->s.update("UPDATE entitlement_private.limits SET valid_until=clock_timestamp()-interval '1 second',valid_from=clock_timestamp()-interval '2 seconds' WHERE tenant_id=? AND resource='TAXPAYERS'",t));
        assertThat(request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(409);
        assertThat(request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"Edit DEMO",List.of())).statusCode()).isEqualTo(200);
        assertThat(archive(t,id,1).statusCode()).isEqualTo(200);assertThat(restore(t,id,2).statusCode()).isEqualTo(409);
        UUID no=firm(ownerId,-1);
        assertThat(request(owner,"POST",path(no),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")).statusCode()).isEqualTo(409);
    }
    List<Integer> race(Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){var barrier=new CyclicBarrier(2);var x=pool.submit(()->{barrier.await();return a.call().statusCode();});var y=pool.submit(()->{barrier.await();return b.call().statusCode();});return List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS)).stream().sorted().toList();}
    }
    @Test void concurrentDuplicateRfcAndLastSlotCannotOverbook() throws Exception {
        UUID t=firm(ownerId,2);assertThat(race(()->request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101001","LEGAL_ENTITY")),()->request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101001","LEGAL_ENTITY")))).containsExactly(201,409);
        assertThat(race(()->request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY")),()->request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101003","LEGAL_ENTITY")))).containsExactly(201,409);
        assertThat(count(t,"taxpayers.taxpayers")).isEqualTo(2);assertThat(count(t,"taxpayers.taxpayer_revisions")).isEqualTo(2);
    }
    @Test void concurrentEditsArchiveAndRestorationAreVersioned() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");assertThat(race(()->request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"A DEMO",List.of())),()->request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(0,"B DEMO",List.of())))).containsExactly(200,409);
        assertThat(race(()->archive(t,id,1),()->request(owner,"PATCH",path(t)+"/"+id,UUID.randomUUID(),edit(1,"C DEMO",List.of())))).containsExactly(200,409);
        if(get(t,id).path("status").asText().equals("ACTIVE"))assertThat(archive(t,id,2).statusCode()).isEqualTo(200);
        long v=get(t,id).path("version").asLong();assertThat(race(()->restore(t,id,v),()->restore(t,id,v))).containsExactly(200,409);
    }
    @Test void concurrentExactIdempotentRetryCreatesOneAggregate() throws Exception {
        UUID t=firm(ownerId,1),cmd=UUID.randomUUID();var b=create("DEM000101001","LEGAL_ENTITY");
        assertThat(race(()->request(owner,"POST",path(t),cmd,b),()->request(owner,"POST",path(t),cmd,b))).containsExactly(200,201);
        assertThat(count(t,"taxpayers.taxpayers")).isEqualTo(1);assertThat(count(t,"taxpayer_private.commands")).isEqualTo(1);assertThat(count(t,"taxpayer_private.grant_reviews")).isEqualTo(3);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"journal.events","journal.outbox","taxpayers.taxpayer_revisions","taxpayers.taxpayer_regime_references","taxpayers.taxpayer_assignments","taxpayer_private.grant_reviews","taxpayer_private.commands"})
    void anySinkFailureRollsBackTheEntireAggregateAndRetrySucceeds(String table) throws Exception {
        UUID t=firm(),cmd=UUID.randomUUID();long before=count(t,"journal.events");
        admin(null,s->{s.execute("CREATE FUNCTION taxpayer_private.fail_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC_SENSITIVE_FAILURE'; END $$");s.execute("CREATE TRIGGER fail_test BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION taxpayer_private.fail_test()");});
        var b=create("DEM000101001","LEGAL_ENTITY");b.put("regimes",List.of(regime("000",null,null)));
        try {var r=request(owner,"POST",path(t),cmd,b);assertThat(r.statusCode()).isEqualTo(500);assertThat(r.body()).doesNotContain("SYNTHETIC_SENSITIVE_FAILURE","DEM000101001","SQL","Cliente DEMO");}
        finally {admin(null,s->{s.execute("DROP TRIGGER fail_test ON "+table);s.execute("DROP FUNCTION taxpayer_private.fail_test()");});}
        for(String empty:List.of("taxpayers.taxpayers","taxpayers.taxpayer_revisions","taxpayers.taxpayer_assignments","taxpayer_private.grant_reviews","taxpayer_private.commands","access_control.resource_grants"))assertThat(count(t,empty)).isZero();
        assertThat(count(t,"journal.events")).isEqualTo(before);assertThat(count(t,"journal.outbox")).isEqualTo(before);assertThat(request(owner,"POST",path(t),cmd,b).statusCode()).isEqualTo(201);
    }
    @Test void auditAndOperationalLogsContainNoFiscalProfile(CapturedOutput output) throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");assertThat(archive(t,id,0).statusCode()).isEqualTo(200);
        admin(t,s->{String journal=s.queryForObject("SELECT jsonb_agg(to_jsonb(e))::text FROM journal.events e",String.class);assertThat(journal).doesNotContain("DEM000101001","Cliente DEMO","legalName","regimes");
            assertThat(s.queryForObject("SELECT jsonb_agg(to_jsonb(c))::text FROM taxpayer_private.commands c",String.class)).doesNotContain("DEM000101001","Cliente DEMO");});
        assertThat(output.getAll()).doesNotContain("DEM000101001","Cliente DEMO","SYNTHETIC_SENSITIVE_FAILURE");
    }
    String proof(String payload) throws Exception {var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(HexFormat.of().parseHex(DB.actorKey),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    JdbcTemplate runtime(Connection c) throws Exception {c.setAutoCommit(false);return new JdbcTemplate(new SingleConnectionDataSource(c,true));}
    void authorize(JdbcTemplate s,UUID t,UUID id,String cap,String username) throws Exception {
        username=username.equals("alice")?owner.username:username.equals("bob")?person.username:other.username;
        String tx=s.queryForObject("SELECT pg_current_xact_id()::text",String.class);long mfa=Instant.now().getEpochSecond();
        s.queryForList("SELECT * FROM identity.bind_actor(?,?,?,?)",OIDC.issuer(),KeycloakTestProvider.subject(username).toString(),mfa,proof(OIDC.issuer()+"\n"+KeycloakTestProvider.subject(username)+"\n"+tx+"\n"+mfa));
        assertThat(s.queryForObject("SELECT access_control.authorize_operation(?,?,'TAXPAYER',?)",String.class,t,cap,id)).isEqualTo("AUTHORIZED");s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,t);
    }
    Connection connection() throws Exception {return DriverManager.getConnection(DB.runtimeUrl(),PostgresTestDatabase.RUNTIME_USER,DB.runtimePassword);}
    @Test void unfilteredRuntimeSqlRespectsPortfolioAndTransactionContextCannotBeForgedOrRetained() throws Exception {
        UUID a=firm(),b=firm(),one=created(a,"DEM000101001"),two=created(a,"DEM000101002");created(b,"DEM000101003");
        admin(a,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE resource_id=?",two));
        try(var c=connection()){var s=runtime(c);authorize(s,a,null,"TAXPAYER_VIEW","alice");
            assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers",UUID.class)).containsExactly(one);
            s.queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,b.toString());assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers",UUID.class)).containsExactly(one);
            assertThatThrownBy(()->s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,b)).isInstanceOf(org.springframework.dao.DataAccessException.class);c.rollback();
            assertThat(s.queryForObject("SELECT identity.current_actor_id()",UUID.class)).isNull();assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers")).isEmpty();c.rollback();
        }
        // One physical pooled connection, reused across three independent transactions.
        try(var pool=new com.zaxxer.hikari.HikariDataSource()){pool.setJdbcUrl(DB.runtimeUrl());pool.setUsername(PostgresTestDatabase.RUNTIME_USER);pool.setPassword(DB.runtimePassword);pool.setMaximumPoolSize(1);
            try(var c=pool.getConnection()){var s=runtime(c);authorize(s,a,one,"TAXPAYER_VIEW","alice");assertThat(s.queryForObject("SELECT count(*) FROM taxpayers.taxpayers",Integer.class)).isEqualTo(1);c.commit();}
            try(var c=pool.getConnection()){var s=runtime(c);assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers")).isEmpty();assertThat(s.queryForObject("SELECT app.current_tenant_id()",UUID.class)).isNull();c.commit();}
            try(var c=pool.getConnection()){var s=runtime(c);authorize(s,b,null,"TAXPAYER_VIEW","alice");assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers",UUID.class)).doesNotContain(one,two);c.commit();}
        }
    }
    @Test void runtimeAndSessionsCannotWriteHistoryReadPrivateDataOrInvokeUnboundCommands() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");
        for(String role:List.of(PostgresTestDatabase.RUNTIME_USER,"fiscuvia_sessions")){String password=role.equals(PostgresTestDatabase.RUNTIME_USER)?DB.runtimePassword:DB.sessionPassword;
            try(var c=DriverManager.getConnection(DB.getJdbcUrl(),role,password)){var s=new JdbcTemplate(new SingleConnectionDataSource(c,true));
                for(String sql:List.of("SELECT * FROM taxpayer_private.commands","SELECT * FROM taxpayer_private.grant_reviews","SELECT * FROM taxpayers.taxpayer_revisions","SELECT * FROM taxpayers.taxpayer_assignments","UPDATE taxpayers.taxpayers SET legal_name='tampered'","TRUNCATE taxpayers.taxpayers","SELECT taxpayer_private.measured_taxpayers('"+t+"')","SELECT taxpayers.command('"+t+"','"+UUID.randomUUID()+"',NULL,NULL,'CREATE','DEM000101002','LEGAL_ENTITY','DEMO',NULL,'[]',NULL)"))assertThatThrownBy(()->s.execute(sql)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            }
        }
        assertThat(get(t,id).path("version").asLong()).isZero();
    }
    @Test void databaseEnforcesCompositeForeignKeysImmutabilityAndCompleteHistory() throws Exception {
        UUID t=firm(),b=firm(),id=created(t,"DEM000101001"),m=member(b,personId,"ACCOUNTANT");
        assertThatThrownBy(()->admin(b,s->s.update("INSERT INTO access_control.resource_grants(tenant_id,membership_id,resource_kind,resource_id,capability,active,approval_reference) VALUES(?,?,'TAXPAYER',?,'TAXPAYER_VIEW',true,?)",b,m,id,UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->admin(t,s->s.update("UPDATE taxpayers.taxpayers SET rfc_normalized='DEM000101002',version=version+1 WHERE id=?",id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->admin(t,s->s.update("UPDATE taxpayers.taxpayers SET legal_name='No snapshot',version=version+1 WHERE id=?",id))).isInstanceOf(SQLException.class);
        for(String table:List.of("taxpayers.taxpayer_revisions","taxpayers.taxpayer_assignments","taxpayer_private.grant_reviews","taxpayer_private.commands"))assertThatThrownBy(()->admin(t,s->s.execute("DELETE FROM "+table))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->admin(t,s->s.execute("DELETE FROM taxpayers.taxpayers"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void membershipRoleChangeWaitsForAuthorizedFiscalReadThenAccessRemainsDenied() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001"),m=member(t,personId,"ACCOUNTANT");grant(t,m,id,"TAXPAYER_VIEW");
        try(var c=connection();var pool=Executors.newSingleThreadExecutor()){var s=runtime(c);authorize(s,t,id,"TAXPAYER_VIEW","bob");
            var pending=pool.submit(()->team(t,m,0,"role","ASSISTANT"));waitForDatabaseWait();assertThat(pending.isDone()).isFalse();assertThat(s.queryForList("SELECT id FROM taxpayers.taxpayers",UUID.class)).containsExactly(id);c.commit();assertThat(pending.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(person.get(path(t)+"/"+id).statusCode()).isEqualTo(404);
    }
    @Test void grantRevocationWaitsForAuthorizedEditAndCannotBeRecoveredByReplay() throws Exception {
        UUID t=firm(),id=created(t,"DEM000101001");UUID cmd=UUID.randomUUID();var b=edit(0,"Edit DEMO",List.of());
        try(var c=connection();var pool=Executors.newFixedThreadPool(2)){var s=runtime(c);authorize(s,t,id,"TAXPAYER_EDIT","alice");
            var revoked=pool.submit(()->{admin(t,j->j.update("UPDATE access_control.resource_grants SET active=false WHERE resource_id=? AND capability='TAXPAYER_EDIT'",id));return true;});waitForDatabaseWait();assertThat(revoked.isDone()).isFalse();
            String tx=s.queryForObject("SELECT pg_current_xact_id()::text",String.class);UUID correlation=UUID.randomUUID();s.queryForObject("SELECT journal.bind_command(?,?,?)",Object.class,cmd,correlation,proof("journal\n"+tx+"\n"+correlation+"\n"+cmd));
            s.queryForObject("SELECT taxpayers.command(?,?,?,0,'UPDATE_PROFILE',NULL,NULL,'Edit DEMO',NULL,'[]',NULL)",Object.class,t,cmd,id);
            c.commit();assertThat(revoked.get(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(request(owner,"PATCH",path(t)+"/"+id,cmd,b).statusCode()).isEqualTo(404);assertThat(get(t,id).path("version").asLong()).isEqualTo(1);
    }
    @Test void listContractAndQuotaMeasurementIncludeOnlyRealActiveTaxpayers() throws Exception {
        UUID t=firm(ownerId,2),id=created(t,"DEM000101001");created(t,"DEM000101002");
        var response=evidence("GET",path(t),owner.get(path(t)));assertThat(response.statusCode()).isEqualTo(200);
        admin(t,s->s.update("UPDATE access_control.resource_grants SET active=false WHERE resource_id=? AND capability='TAXPAYER_VIEW'",id));
        var usage=OidcBrowser.json(owner.get("/api/v1/tenants/"+t+"/entitlements"));assertThat(usage.get(1).path("measured").asLong()).isEqualTo(2);assertThat(usage.get(1).path("available").asLong()).isZero();
        assertThat(archive(t,id,0).statusCode()).isEqualTo(200);usage=OidcBrowser.json(owner.get("/api/v1/tenants/"+t+"/entitlements"));assertThat(usage.get(1).path("measured").asLong()).isEqualTo(1);assertThat(usage.get(1).path("available").asLong()).isEqualTo(1);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"DEÑ000101001","D&M000101001","Ñ&MO000101001"})
    void postgresAndJavaAgreeOnTheApprovedSpecialRfcAlphabet(String rfc) throws Exception {
        UUID t=firm();String personType=rfc.length()==13?"INDIVIDUAL":"LEGAL_ENTITY";
        assertThat(request(owner,"POST",path(t),UUID.randomUUID(),create(rfc,personType)).statusCode()).isEqualTo(201);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"DEM000230001","DEM990229001","DEM000431001","DEM00010100","DEMO000101001","DÉM000101001","XAXX010101000","XEXX010101000"})
    void invalidRfcIsRejectedAtHttpBoundaryWithoutAnyAggregate(String rfc) throws Exception {
        UUID t=firm();String type=rfc.startsWith("X")?"INDIVIDUAL":"LEGAL_ENTITY";
        var r=request(owner,"POST",path(t),UUID.randomUUID(),create(rfc,type));assertThat(r.statusCode()).isEqualTo(422);assertThat(r.body()).doesNotContain(rfc,"SQL");assertThat(count(t,"taxpayers.taxpayers")).isZero();
    }
    @Test void concurrentCreateAndRestoreCompeteForOneRealActiveSlot() throws Exception {
        UUID t=firm(ownerId,1),id=created(t,"DEM000101001");assertThat(archive(t,id,0).statusCode()).isEqualTo(200);
        assertThat(race(()->restore(t,id,1),()->request(owner,"POST",path(t),UUID.randomUUID(),create("DEM000101002","LEGAL_ENTITY"))))
            .isIn(List.of(200,409),List.of(201,409));
        // CREATE wins with 201, RESTORE wins with 200; exactly one must fail capacity.
        admin(t,s->assertThat(s.queryForObject("SELECT count(*) FROM taxpayers.taxpayers WHERE status='ACTIVE'",Integer.class)).isEqualTo(1));
    }
    void waitForDatabaseWait() throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<deadline){boolean[] waiting={false};admin(null,s->waiting[0]=s.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE NOT l.granted AND a.datname=current_database())",Boolean.class));if(waiting[0])return;java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));}throw new AssertionError("Expected real database lock wait");}
    void guard() throws Exception {((org.springframework.boot.ApplicationRunner)application.getBean("taxpayerGuards")).run(null);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"RLS","HISTORY","PRIVILEGES","MFA","EXTRA_POLICY"})
    void startupGuardRejectsPolicyOrDatabaseProtectionDrift(String change) throws Exception {
        String undo=switch(change){case "RLS"->"ALTER TABLE taxpayer_private.operation_scope FORCE ROW LEVEL SECURITY";case "HISTORY"->"ALTER TABLE taxpayers.taxpayer_revisions ENABLE TRIGGER append_only";case "PRIVILEGES"->"REVOKE SELECT ON taxpayer_private.grant_reviews FROM fiscuvia_runtime";case "MFA"->"UPDATE access_control_private.role_capabilities SET requires_mfa=true WHERE capability='TAXPAYER_CREATE'";default->"DELETE FROM access_control_private.role_capabilities WHERE role_code='ASSISTANT' AND capability='TAXPAYER_VIEW'";};
        String mutate=switch(change){case "RLS"->"ALTER TABLE taxpayer_private.operation_scope NO FORCE ROW LEVEL SECURITY";case "HISTORY"->"ALTER TABLE taxpayers.taxpayer_revisions DISABLE TRIGGER append_only";case "PRIVILEGES"->"GRANT SELECT ON taxpayer_private.grant_reviews TO fiscuvia_runtime";case "MFA"->"UPDATE access_control_private.role_capabilities SET requires_mfa=false WHERE capability='TAXPAYER_CREATE'";default->"INSERT INTO access_control_private.role_capabilities VALUES('ASSISTANT','TAXPAYER_VIEW','TAXPAYER',false,'SYNTHETIC DRIFT')";};
        admin(null,s->s.execute(mutate));try {assertThatThrownBy(this::guard).isInstanceOf(IllegalStateException.class);}finally {admin(null,s->s.execute(undo));}guard();
    }
}
