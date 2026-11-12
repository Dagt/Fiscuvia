package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Every positive uses real signed OIDC, the original invitation acceptance and production RLS/quota/audit. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @Timeout(60)
class EmailInvitationTests {
 static final LocalTls TLS=new LocalTls();
 @Container static final PostgresTestDatabase DB=new PostgresTestDatabase();
 @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){DB.register(r);TLS.register(r);r.add("fiscuvia.identity.enabled",()->true);r.add("fiscuvia.identity.issuer",OIDC::issuer);r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");r.add("fiscuvia.identity.contact-policy",()->"keycloak-local-verified-email-v1");}
 static OidcBrowser owner,target,unverified;static UUID ownerId,targetId;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext application;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.session.SessionRepository<? extends org.springframework.session.Session> sessions;
 @BeforeEach void identities() throws Exception {if(owner!=null)return;owner=new OidcBrowser(TLS,OIDC);target=new OidcBrowser(TLS,OIDC);unverified=new OidcBrowser(TLS,OIDC);ownerId=UUID.fromString(owner.login("alice").path("userId").asText());targetId=UUID.fromString(target.login("bob").path("userId").asText());unverified.login("carol");}
 @AfterAll static void close(){if(owner!=null)owner.close();if(target!=null)target.close();if(unverified!=null)unverified.close();}
 void admin(UUID tenant,Consumer<JdbcTemplate> work) throws Exception {try(var c=DriverManager.getConnection(DB.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DB.migrationPassword)){c.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));if(tenant!=null)sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);work.accept(sql);c.commit();}}
 UUID firm(UUID user) throws Exception {UUID[] id={null};admin(null,s->id[0]=s.queryForObject("SELECT tenant_private.provision(?,?,?,'Equipo DEMO',NULL,NULL,'America/Mexico_City','es-MX')",UUID.class,UUID.randomUUID(),user,UUID.randomUUID()));admin(id[0],s->QuotaFixtures.seed(s,id[0]));return id[0];}
 UUID firm() throws Exception{return firm(ownerId);}
 String path(UUID t){return "/api/v1/tenants/"+t+"/team";}
 String address(){return target.username+"@example.invalid";}
 HttpResponse<String> invite(OidcBrowser b,UUID tenant,UUID key,String email,String role) throws Exception {
  return b.client.send(HttpRequest.newBuilder(URI.create(TLS.origin()+path(tenant)+"/invitations"))
   .header("Content-Type","application/json").header("Idempotency-Key",key.toString()).header("X-CSRF-TOKEN",b.csrf())
   .POST(HttpRequest.BodyPublishers.ofString(OidcBrowser.JSON.writeValueAsString(Map.of("email",email,"role",role)))).build(),HttpResponse.BodyHandlers.ofString());
 }
 tools.jackson.databind.JsonNode create(UUID t) throws Exception {var r=invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT");assertThat(r.statusCode()).isEqualTo(201);return OidcBrowser.json(r);}
 HttpResponse<String> accept(OidcBrowser b,String token) throws Exception{return b.send("POST","/api/v1/auth/invitations/accept",OidcBrowser.JSON.writeValueAsString(Map.of("token",token)),b.csrf());}
 int count(UUID tenant,String table) throws Exception {int[] n={0};admin(tenant,s->n[0]=s.queryForObject("SELECT count(*) FROM "+table,Integer.class));return n[0];}
 @Test void callbackPersistsOnlyVerifiedContactsWithExactExternalIdentity() throws Exception {
  admin(null,s->{assertThat(s.queryForObject("SELECT email FROM identity_private.verified_contacts WHERE issuer=? AND subject=?",String.class,OIDC.issuer(),KeycloakTestProvider.subject(target.username).toString())).isEqualTo(address());
   assertThat(s.queryForObject("SELECT count(*) FROM identity_private.verified_contacts WHERE subject=?",Integer.class,KeycloakTestProvider.subject(unverified.username).toString())).isZero();});
 }
 @Test void emailCreationUsesOriginalExactRecipientAcceptanceAndAtomicJournalOutbox() throws Exception {
  UUID t=firm();var i=create(t);String token=i.path("token").asText();assertThat(token).matches("[A-Za-z0-9_-]{43}");assertThat(i.path("delivery").asText()).isEqualTo("MANUAL");
  admin(t,s->{var r=s.queryForMap("SELECT issuer,subject,token_hash FROM identity.invitations WHERE id=?",UUID.fromString(i.path("invitationId").asText()));assertThat(r.get("issuer")).isEqualTo(OIDC.issuer());assertThat(r.get("subject")).isEqualTo(KeycloakTestProvider.subject(target.username).toString());assertThat((byte[])r.get("token_hash")).hasSize(32).isNotEqualTo(token.getBytes());});
  assertThat(accept(target,token).statusCode()).isEqualTo(200);assertThat(count(t,"identity.memberships")).isEqualTo(2);
  assertThat(count(t,"journal.events")).isEqualTo(4);assertThat(count(t,"journal.outbox")).isEqualTo(4);
 }
 @Test void expiredInvitationCannotBeAccepted() throws Exception {UUID t=firm();var i=create(t);admin(t,s->s.update("UPDATE identity.invitations SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?",UUID.fromString(i.path("invitationId").asText())));assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(403);assertThat(count(t,"identity.memberships")).isEqualTo(1);}
 @Test void revokedInvitationCannotBeAccepted() throws Exception {UUID t=firm();var i=create(t);assertThat(owner.send("POST","/api/v1/auth/invitations/revoke",OidcBrowser.JSON.writeValueAsString(Map.of("tenantId",t,"invitationId",i.path("invitationId").asText())),owner.csrf()).statusCode()).isEqualTo(204);assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(403);}
 @Test void consumedInvitationCannotBeReused() throws Exception {var i=create(firm());String token=i.path("token").asText();assertThat(accept(target,token).statusCode()).isEqualTo(200);assertThat(accept(target,token).statusCode()).isEqualTo(403);}
 @Test void emailAndTokenAreInsufficientWithoutExactOidcSubject() throws Exception {var i=create(firm());assertThat(accept(owner,i.path("token").asText()).statusCode()).isEqualTo(403);assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(200);}
 @Test void replayReturnsMetadataWithoutTheTokenAndDifferentPayloadConflicts() throws Exception {
  UUID t=firm(),key=UUID.randomUUID();var first=invite(owner,t,key,address(),"ACCOUNTANT");var again=invite(owner,t,key,address(),"ACCOUNTANT");
  assertThat(first.statusCode()).isEqualTo(201);assertThat(again.statusCode()).isEqualTo(200);assertThat(OidcBrowser.json(again).path("token").isNull()).isTrue();assertThat(OidcBrowser.json(again).path("replayed").asBoolean()).isTrue();assertThat(again.body()).doesNotContain(OidcBrowser.json(first).path("token").asText());
  assertThat(invite(owner,t,key,address(),"ASSISTANT").statusCode()).isEqualTo(409);assertThat(count(t,"identity.invitations")).isEqualTo(1);assertThat(count(t,"journal.events")).isEqualTo(3);
 }
 @Test void concurrentReplayShowsOnlyOneSecretAndCreatesOneInvitation() throws Exception {
  UUID t=firm(),key=UUID.randomUUID();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CyclicBarrier(2);Callable<HttpResponse<String>> task=()->{gate.await(5,TimeUnit.SECONDS);return invite(owner,t,key,address(),"ACCOUNTANT");};var a=workers.submit(task);var b=workers.submit(task);var replies=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertThat(replies).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201,200);assertThat(replies.stream().filter(r->!OidcBrowser.json(r).path("token").isNull()).count()).isEqualTo(1);assertThat(count(t,"identity.invitations")).isEqualTo(1);}
 }
 @Test void concurrentDistinctCommandsCannotIssueDuplicatePendingRecipients() throws Exception {
  UUID t=firm();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CyclicBarrier(2);Callable<Integer> task=()->{gate.await(5,TimeUnit.SECONDS);return invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode();};var a=workers.submit(task);var b=workers.submit(task);assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);assertThat(count(t,"identity.invitations")).isEqualTo(1);}
 }
 @Test void unknownUnverifiedAndAmbiguousAddressesFailWithoutEnumeration() throws Exception {
  UUID t=firm();for(String email:List.of("missing@example.invalid",unverified.username+"@example.invalid")){var r=invite(owner,t,UUID.randomUUID(),email,"ACCOUNTANT");assertThat(r.statusCode()).isEqualTo(422);assertThat(r.body()).doesNotContain(email,"subject","issuer","SQL");}
  admin(null,s->s.update("UPDATE identity_private.verified_contacts SET email=? WHERE subject=?",address(),KeycloakTestProvider.subject(owner.username).toString()));
  try{assertThat(invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode()).isEqualTo(422);}finally{admin(null,s->s.update("UPDATE identity_private.verified_contacts SET email=? WHERE subject=?",owner.username+"@example.invalid",KeycloakTestProvider.subject(owner.username).toString()));}
 }
 @Test void recipientAlreadyInTheFirmCannotReceiveAnotherMembershipInvitation() throws Exception {UUID t=firm();var i=create(t);accept(target,i.path("token").asText());assertThat(invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode()).isEqualTo(422);assertThat(count(t,"identity.memberships")).isEqualTo(2);}
 @Test void anonymousAndOtherTenantCannotIssueOrInspectOptions() throws Exception {
  UUID t=firm();try(var anonymous=new OidcBrowser(TLS,OIDC)){assertThat(anonymous.get(path(t)+"/invitation-options").statusCode()).isEqualTo(401);}
  assertThat(target.get(path(t)+"/invitation-options").statusCode()).isEqualTo(403);assertThat(invite(target,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode()).isEqualTo(403);
  UUID b=firm(targetId);assertThat(owner.get(path(b)+"/invitation-options").statusCode()).isEqualTo(403);assertThat(count(t,"identity.invitations")).isZero();
 }
 @Test void nonOwnerRolesRemainDenied() throws Exception {
  for(String role:List.of("FIRM_SUPERVISOR","ACCOUNTANT","ASSISTANT")){UUID t=firm(),member=UUID.randomUUID();admin(t,s->{s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",t,member,targetId);s.update("INSERT INTO identity.membership_roles VALUES(?,?,?)",t,member,role);});assertThat(target.get(path(t)+"/invitation-options").statusCode()).isEqualTo(403);assertThat(invite(target,t,UUID.randomUUID(),"missing@example.invalid",role).statusCode()).isEqualTo(403);}
 }
 @Test void rolesIdsAndClaimsInRequestCannotUpgradeInvitationAuthority() throws Exception {
  UUID t=firm();assertThat(invite(owner,t,UUID.randomUUID(),address(),"FIRM_OWNER_ADMIN").statusCode()).isEqualTo(422);
  assertThat(owner.send("POST",path(t)+"/invitations","{\"email\":\"missing@example.invalid\",\"role\":\"ACCOUNTANT\",\"subject\":\"forged\"}",owner.csrf()).statusCode()).isEqualTo(400);
 }
 @Test void csrfAndIdempotencyHeadersAreRequired() throws Exception {UUID t=firm();assertThat(owner.send("POST",path(t)+"/invitations","{\"email\":\"x@example.invalid\",\"role\":\"ACCOUNTANT\"}",null).statusCode()).isEqualTo(403);assertThat(owner.send("POST",path(t)+"/invitations","{\"email\":\"x@example.invalid\",\"role\":\"ACCOUNTANT\"}",owner.csrf()).statusCode()).isEqualTo(400);}
 @Test void quotaStillControlsOriginalAcceptanceAndPendingDoesNotConsume() throws Exception {UUID t=firm();admin(t,s->s.update("UPDATE entitlement_private.limits SET limit_value=1 WHERE tenant_id=? AND resource='USERS'",t));var i=create(t);assertThat(count(t,"identity.memberships")).isEqualTo(1);assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(409);assertThat(count(t,"identity.memberships")).isEqualTo(1);admin(t,s->s.update("UPDATE entitlement_private.limits SET limit_value=2 WHERE tenant_id=? AND resource='USERS'",t));assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(200);}
 @Test void suspendedTenantCannotIssueInvitations() throws Exception {UUID t=firm();admin(null,s->s.queryForObject("SELECT tenant_private.change_status(?,?,?,0,'SUSPENDED')",Long.class,t,UUID.randomUUID(),UUID.randomUUID()));assertThat(invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode()).isEqualTo(403);}
 @Test void teamPermissionsMatchTheSubsequentLimitedApproval() throws Exception {admin(null,s->assertThat(s.queryForObject("SELECT count(*) FROM access_control_private.role_capabilities WHERE capability IN ('MEMBERSHIP_VIEW','MEMBERSHIP_ASSIGN_ROLE','MEMBERSHIP_REACTIVATE') AND role_code='FIRM_OWNER_ADMIN' AND requires_mfa",Integer.class)).isEqualTo(3));assertThat(owner.get(path(firm())).statusCode()).isEqualTo(403);}
 @Test void journalFailureRollsBackEmailInvitationAndReceipt() throws Exception {failedSink("events");}
 @Test void outboxFailureRollsBackEmailInvitationAndReceipt() throws Exception {failedSink("outbox");}
 void failedSink(String table) throws Exception {UUID t=firm(),key=UUID.randomUUID();admin(t,s->{s.execute("CREATE FUNCTION identity_private.fail_33() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC_SINK_FAILURE'; END $$");s.execute("CREATE TRIGGER fail_33 BEFORE INSERT ON journal."+table+" FOR EACH ROW EXECUTE FUNCTION identity_private.fail_33()");});try{var r=invite(owner,t,key,address(),"ACCOUNTANT");assertThat(r.statusCode()).isEqualTo(500);assertThat(r.body()).doesNotContain("SYNTHETIC_SINK_FAILURE",address(),"SQL");}finally{admin(t,s->{s.execute("DROP TRIGGER fail_33 ON journal."+table);s.execute("DROP FUNCTION identity_private.fail_33()");});}assertThat(count(t,"identity.invitations")).isZero();assertThat(count(t,"identity_private.email_invitation_commands")).isZero();assertThat(invite(owner,t,key,address(),"ACCOUNTANT").statusCode()).isEqualTo(201);}
 @Test void runtimeAndSessionRolesCannotReadPrivateContactDataOrReceipts() throws Exception {for(var role:Map.of(PostgresTestDatabase.RUNTIME_USER,DB.runtimePassword,"fiscuvia_sessions",DB.sessionPassword).entrySet()){try(var c=DriverManager.getConnection(DB.getJdbcUrl(),role.getKey(),role.getValue());var s=c.createStatement()){for(String table:List.of("verified_contacts","email_invitation_commands"))assertThatThrownBy(()->s.execute("SELECT * FROM identity_private."+table)).isInstanceOf(SQLException.class);assertThatThrownBy(()->s.execute("SELECT identity.record_verified_local_contact('forged','forged','victim@example.invalid',NULL,repeat('0',64))")).isInstanceOf(SQLException.class);}}}
 @Test void contactsAndSecretsNeverEnterLogsOrStoredSessionPrincipal(CapturedOutput output) throws Exception {
  var i=create(firm());String token=i.path("token").asText();assertThat(output.getAll()).doesNotContain(address(),token,OIDC.clientSecret,OIDC.password);
  try(var c=DriverManager.getConnection(DB.getJdbcUrl(),"fiscuvia_sessions",DB.sessionPassword);var s=c.createStatement();var r=s.executeQuery("SELECT attribute_bytes FROM auth_sessions.spring_session_attributes WHERE attribute_name='SPRING_SECURITY_CONTEXT'")){while(r.next())assertThat(new String(r.getBytes(1),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(address(),token,"Demostración","OidcUser","access_token");}
 }
 @Test void disablingDuplicateProtectionTripsTheStartupGuard() throws Exception {admin(null,s->s.execute("ALTER TABLE identity.invitations DISABLE TRIGGER invitation_recipient_unique"));try{assertThatThrownBy(this::guard).hasRootCauseInstanceOf(IllegalStateException.class);}finally{admin(null,s->s.execute("ALTER TABLE identity.invitations ENABLE TRIGGER invitation_recipient_unique"));}guard();}
 @Test void ownerWithoutRecentMfaCannotUseTheEmailAdapter() throws Exception {
  UUID t=firm();String id=new String(Base64.getDecoder().decode(owner.sessionCookie()),java.nio.charset.StandardCharsets.UTF_8);
  org.springframework.session.Session session=sessions.findById(id);org.springframework.security.core.context.SecurityContext context=session.getAttribute("SPRING_SECURITY_CONTEXT");
  var auth=context.getAuthentication();Object original=auth.getPrincipal();var fields=original.getClass().getRecordComponents();
  var constructor=original.getClass().getDeclaredConstructor(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));constructor.setAccessible(true);
  try{for(long time:List.of(0L,java.time.Instant.now().getEpochSecond()-601)){
   Object[] values=new Object[fields.length];for(int n=0;n<fields.length;n++){var accessor=fields[n].getAccessor();accessor.setAccessible(true);values[n]=fields[n].getName().equals("mfaAt")?time:accessor.invoke(original);}
   var principal=(org.springframework.security.oauth2.core.user.OAuth2User)constructor.newInstance(values);
   context.setAuthentication(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,auth.getAuthorities(),"local"));session.setAttribute("SPRING_SECURITY_CONTEXT",context);save(session);
   assertThat(owner.get(path(t)+"/invitation-options").statusCode()).isEqualTo(403);assertThat(invite(owner,t,UUID.randomUUID(),address(),"ACCOUNTANT").statusCode()).isEqualTo(403);
  }}finally{context.setAuthentication(auth);session.setAttribute("SPRING_SECURITY_CONTEXT",context);save(session);}assertThat(count(t,"identity.invitations")).isZero();
 }
 @SuppressWarnings({"rawtypes","unchecked"}) void save(org.springframework.session.Session s){((org.springframework.session.SessionRepository)sessions).save(s);}
 @Test void actorHmacCannotBeReusedToForgeContactClaims() throws Exception {
  try(var c=DriverManager.getConnection(DB.getJdbcUrl(),PostgresTestDatabase.RUNTIME_USER,DB.runtimePassword)){
   c.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));String tx=sql.queryForObject("SELECT pg_current_xact_id()::text",String.class);
   long mfa=java.time.Instant.now().getEpochSecond();String subject=KeycloakTestProvider.subject(owner.username).toString();
   var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(HexFormat.of().parseHex(DB.actorKey),"HmacSHA256"));
   String proof=HexFormat.of().formatHex(mac.doFinal((OIDC.issuer()+"\n"+subject+"\n"+tx+"\n"+mfa).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   sql.queryForList("SELECT * FROM identity.bind_actor(?,?,?,?)",OIDC.issuer(),subject,mfa,proof);
   assertThatThrownBy(()->sql.queryForObject("SELECT identity.record_verified_local_contact(?,?,?,'Forged',?)",Object.class,OIDC.issuer(),subject,address(),proof)).isInstanceOf(org.springframework.dao.DataAccessException.class);c.rollback();
  }
  admin(null,s->assertThat(s.queryForObject("SELECT email FROM identity_private.verified_contacts WHERE issuer=? AND subject=?",String.class,OIDC.issuer(),KeycloakTestProvider.subject(owner.username).toString())).isEqualTo(owner.username+"@example.invalid"));
 }
 @Test void emailLocatorChangesDoNotRetargetAnAlreadyIssuedInvitation() throws Exception {
  var i=create(firm());admin(null,s->{s.update("UPDATE identity_private.verified_contacts SET email='changed@example.invalid' WHERE subject=?",KeycloakTestProvider.subject(target.username).toString());s.update("UPDATE identity_private.verified_contacts SET email=? WHERE subject=?",address(),KeycloakTestProvider.subject(owner.username).toString());});
  try{assertThat(accept(owner,i.path("token").asText()).statusCode()).isEqualTo(403);assertThat(accept(target,i.path("token").asText()).statusCode()).isEqualTo(200);}
  finally{admin(null,s->{s.update("UPDATE identity_private.verified_contacts SET email=? WHERE subject=?",address(),KeycloakTestProvider.subject(target.username).toString());s.update("UPDATE identity_private.verified_contacts SET email=? WHERE subject=?",owner.username+"@example.invalid",KeycloakTestProvider.subject(owner.username).toString());});}
 }
 @Test void excessiveContactGrantTripsTheGuardWithoutBeingUsedForTests() throws Exception {
  admin(null,s->s.execute("GRANT SELECT ON identity_private.verified_contacts TO fiscuvia_runtime"));
  try{assertThatThrownBy(this::guard).hasRootCauseInstanceOf(IllegalStateException.class);}finally{admin(null,s->s.execute("REVOKE SELECT ON identity_private.verified_contacts FROM fiscuvia_runtime"));}guard();
 }
 void guard() throws Exception {Object bean=application.getBean("localInvitationGuards");var method=bean.getClass().getDeclaredMethod("run",org.springframework.boot.ApplicationArguments.class);method.setAccessible(true);method.invoke(bean,new org.springframework.boot.DefaultApplicationArguments());}
}
