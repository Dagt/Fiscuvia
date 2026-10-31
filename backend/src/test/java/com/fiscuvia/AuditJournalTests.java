package com.fiscuvia;

import com.fiscuvia.audit.*;
import com.fiscuvia.identity.*;
import com.fiscuvia.platform.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AuditJournalTests.JournalFixtureController.class) @Timeout(90)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AuditJournalTests {
    static final LocalTls TLS=new LocalTls();
    @Container static final PostgresTestDatabase DATABASE=new PostgresTestDatabase();
    @Container static final KeycloakTestProvider OIDC=new KeycloakTestProvider(TLS.origin());
    static final String ROOT="/api/v1/auth/invitations/__journal-test/";
    @Autowired DataSource runtime;
    @Autowired AuditJournal journal;
    @Autowired org.springframework.context.ApplicationContext context;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){DATABASE.register(r);TLS.register(r);
        r.add("fiscuvia.identity.enabled",()->true);r.add("fiscuvia.identity.issuer",OIDC::issuer);
        r.add("fiscuvia.identity.client-secret",()->OIDC.clientSecret);r.add("fiscuvia.identity.mfa-policy",()->"keycloak-local-otp-v1");}
    @BeforeAll static void fixtureSchema() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword)
            .defaultSchema("infrastructure").locations("classpath:db/migration").load().migrate();
        try(var c=migration();var s=c.createStatement();var in=AuditJournalTests.class.getResourceAsStream("/journal-fixture.sql")){
            s.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Test void committedOperationAndOutboxHaveVerifiedActorTenantAndCorrelation() throws Exception {
        try(var f=new Fixture(false)){
            UUID spoof=UUID.randomUUID(),command=UUID.randomUUID();
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(TLS.origin()+ROOT+f.tenant+"/command/"+command))
                .header("X-CSRF-TOKEN",f.browser.csrf()).header("X-Correlation-Id",spoof.toString()).header("X-User-Id",spoof.toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            var response=f.browser.client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);
            var events=rows(f.tenant,"SELECT * FROM journal.events");assertThat(events).hasSize(1);
            var event=events.getFirst();assertThat(event.get("actor_id")).isEqualTo(f.user);assertThat(event.get("tenant_id")).isEqualTo(f.tenant);
            assertThat(event.get("correlation_id").toString()).isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow()).isNotEqualTo(spoof.toString());
            assertThat(event.get("event_code")).isEqualTo("AUTHORIZED_OPERATION_RECORDED");assertThat(event.get("schema_version")).isEqualTo(1);
            var out=rows(f.tenant,"SELECT * FROM journal.outbox").getFirst();assertThat(out.get("event_id")).isEqualTo(event.get("event_id"));
            assertThat(out.get("causation_id")).isEqualTo(event.get("event_id"));assertThat(out.get("correlation_id")).isEqualTo(event.get("correlation_id"));
            assertThat(out.get("payload").toString()).isEqualTo("{\"auditEventId\": \""+event.get("event_id")+"\"}");assertThat(value(f)).isEqualTo(1);
        }
    }
    @Test void failureBeforeWriteProducesNoSuccessOrOutbox() throws Exception { rollback("before"); }
    @Test void failureAfterWriteRollsBackChangeAndJournal() throws Exception { rollback("after"); }
    void rollback(String phase) throws Exception {try(var f=new Fixture(false)){
        assertThat(f.command(UUID.randomUUID(),phase).statusCode()).isEqualTo(409);assertThat(value(f)).isZero();
        assertThat(rows(f.tenant,"SELECT * FROM journal.events")).isEmpty();assertThat(rows(f.tenant,"SELECT * FROM journal.outbox")).isEmpty();}}
    @Test void auditInsertFailureBlocksSensitiveMembershipCommit() throws Exception {
        try(var owner=new Fixture(true);var recipient=new OidcBrowser(TLS,OIDC)){
            recipient.login("bob");UUID member=member(owner.tenant,UUID.fromString(OidcBrowser.json(recipient.get("/api/v1/me")).path("userId").asText()));
            admin(null,s->s.execute("CREATE TRIGGER force_failure BEFORE INSERT ON journal.events FOR EACH ROW EXECUTE FUNCTION journal_fixture.fail_insert()"));
            try {assertThat(owner.status(member,"SUSPENDED").statusCode()).isEqualTo(500);}
            finally {admin(null,s->s.execute("DROP TRIGGER force_failure ON journal.events"));}
            assertThat(rows(owner.tenant,"SELECT status FROM identity.memberships WHERE id='"+member+"'").getFirst().get("status")).isEqualTo("ACTIVE");
            assertThat(rows(owner.tenant,"SELECT * FROM journal.events WHERE event_code='MEMBERSHIP_SUSPENDED'")).isEmpty();
        }
    }
    @Test void outboxInsertFailureRollsBackOperationAndAudit() throws Exception {
        try(var f=new Fixture(false)){
            admin(null,s->s.execute("CREATE TRIGGER force_failure BEFORE INSERT ON journal.outbox FOR EACH ROW EXECUTE FUNCTION journal_fixture.fail_insert()"));
            try {assertThat(f.command(UUID.randomUUID(),"normal").statusCode()).isEqualTo(500);}
            finally {admin(null,s->s.execute("DROP TRIGGER force_failure ON journal.outbox"));}
            assertThat(value(f)).isZero();assertThat(rows(f.tenant,"SELECT * FROM journal.events")).isEmpty();
        }
    }
    @Test void rollbackAfterJournalAppendLeavesNeitherSuccessNorOutbox() throws Exception {
        try(var f=new Fixture(false)) {assertThat(f.command(UUID.randomUUID(),"after-journal").statusCode()).isEqualTo(409);
            assertThat(value(f)).isZero();assertThat(rows(f.tenant,"SELECT * FROM journal.events")).isEmpty();assertThat(rows(f.tenant,"SELECT * FROM journal.outbox")).isEmpty();}
    }
    @Test void retriesDoNotRepeatTheCommandEffectOrDuplicateEvents() throws Exception {
        try(var f=new Fixture(false)){UUID command=UUID.randomUUID();var first=f.command(command,"normal");var second=f.command(command,"normal");
            assertThat(first.statusCode()).isEqualTo(200);assertThat(second.statusCode()).isEqualTo(200);
            assertThat(OidcBrowser.json(second).path("replay").asBoolean()).isTrue();
            assertThat(OidcBrowser.json(first).path("eventId")).isEqualTo(OidcBrowser.json(second).path("eventId"));
            assertThat(value(f)).isEqualTo(1);assertThat(rows(f.tenant,"SELECT * FROM journal.events")).hasSize(1);assertThat(rows(f.tenant,"SELECT * FROM journal.outbox")).hasSize(1);}
    }
    @Test void concurrentIdenticalCommandHasOneEffectAndOneReplay() throws Exception {
        try(var f=new Fixture(false);var executor=Executors.newVirtualThreadPerTaskExecutor()) {UUID command=UUID.randomUUID();String csrf=f.browser.csrf();
            var a=executor.submit(()->f.browser.send("POST",ROOT+f.tenant+"/command/"+command,null,csrf));
            var b=executor.submit(()->f.browser.send("POST",ROOT+f.tenant+"/command/"+command,null,csrf));
            var first=a.get();var second=b.get();assertThat(first.statusCode()).isEqualTo(200);assertThat(second.statusCode()).isEqualTo(200);
            assertThat(List.of(OidcBrowser.json(first).path("replay").asBoolean(),OidcBrowser.json(second).path("replay").asBoolean())).containsExactlyInAnyOrder(false,true);
            assertThat(value(f)).isEqualTo(1);assertThat(rows(f.tenant,"SELECT * FROM journal.events")).hasSize(1);}
    }
    @Test void successfulBootstrapRecordsAdministrativeAttributionNotAnImpersonatedUser() throws Exception {
        try(var f=new Fixture(true)){var event=rows(f.tenant,"SELECT * FROM journal.events").getFirst();
            assertThat(event.get("event_code")).isEqualTo("OWNER_BOOTSTRAPPED");assertThat(event.get("actor_kind")).isEqualTo("DATABASE_ADMIN");
            assertThat(event.get("actor_id")).isNull();assertThat(event.get("approval_reference")).isEqualTo(f.approval);
            assertThat(event.get("resource_id")).isEqualTo(f.membership);assertThat(event.get("correlation_id")).isNotNull();}
    }
    @Test void realInvitationIssuedRevokedAndAcceptedEventsAreAtomic() throws Exception {
        try(var f=new Fixture(true);var bob=new OidcBrowser(TLS,OIDC);var carol=new OidcBrowser(TLS,OIDC)){
            bob.login("bob");carol.login("carol");var first=f.invite(bob);var second=f.invite(carol);
            assertThat(f.browser.send("POST","/api/v1/auth/invitations/revoke","{\"tenantId\":\""+f.tenant+"\",\"invitationId\":\""+first.path("invitationId").asText()+"\"}",f.browser.csrf()).statusCode()).isEqualTo(204);
            assertThat(carol.send("POST","/api/v1/auth/invitations/accept","{\"token\":\""+second.path("token").asText()+"\"}",carol.csrf()).statusCode()).isEqualTo(200);
            var events=rows(f.tenant,"SELECT event_code,causation_id FROM journal.events ORDER BY occurred_at,event_id");
            assertThat(events.stream().map(e->e.get("event_code")).toList()).containsExactly("OWNER_BOOTSTRAPPED","INVITATION_ISSUED","INVITATION_ISSUED","INVITATION_REVOKED","INVITATION_ACCEPTED");
            assertThat(events.get(3).get("causation_id")).isNotNull();assertThat(events.get(4).get("causation_id")).isNotNull();
            assertThat(rows(f.tenant,"SELECT * FROM journal.outbox")).hasSize(5);
        }
    }
    @Test void suspensionAndRevocationKeepOriginalAuthorshipAndDoNotDuplicateNoop() throws Exception {
        try(var f=new Fixture(true);var bob=new OidcBrowser(TLS,OIDC)){
            UUID user=UUID.fromString(bob.login("bob").path("userId").asText()),member=member(f.tenant,user);
            assertThat(f.status(member,"SUSPENDED").statusCode()).isEqualTo(204);assertThat(f.status(member,"REVOKED").statusCode()).isEqualTo(204);
            assertThat(f.status(member,"REVOKED").statusCode()).isEqualTo(204);
            var events=rows(f.tenant,"SELECT actor_id,event_code FROM journal.events WHERE actor_kind='USER' ORDER BY occurred_at");
            assertThat(events).hasSize(2);assertThat(events.stream().map(e->e.get("actor_id")).toList()).containsOnly(f.user);
            assertThat(events.stream().map(e->e.get("event_code")).toList()).containsExactly("MEMBERSHIP_SUSPENDED","MEMBERSHIP_REVOKED");
            assertThat(bob.send("POST","/api/v1/auth/tenant","{\"tenantId\":\""+f.tenant+"\"}",bob.csrf()).statusCode()).isEqualTo(403);
        }
    }
    @Test void privilegedDenialPersistsAfterRollbackWithoutForeignTenantOrResource() throws Exception {
        try(var f=new Fixture(false)){UUID foreign=UUID.randomUUID();var response=f.browser.send("POST","/api/v1/auth/invitations",
            "{\"tenantId\":\""+foreign+"\",\"subject\":\"DEMO-private-recipient\",\"role\":\"ACCOUNTANT\"}",f.browser.csrf());
            assertThat(response.statusCode()).isEqualTo(403);var denied=global("SELECT * FROM journal_private.denials WHERE actor_id='"+f.user+"'");
            assertThat(denied).hasSize(1);assertThat(denied.getFirst().get("correlation_id").toString()).isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow());
            assertThat(denied.toString()).doesNotContain(foreign.toString(),"DEMO-private-recipient","tenant_id","resource_id");
            assertThat(rows(f.tenant,"SELECT * FROM journal.events")).isEmpty();}
    }
    @Test void runtimeCannotForgeActorProofOrInsertDeniedFactsArbitrarily() throws Exception {
        try(var c=runtime.getConnection();var s=c.createStatement()) {
            assertThatThrownBy(()->s.executeQuery("SELECT journal.bind_command(gen_random_uuid(),gen_random_uuid(),repeat('0',64))")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeQuery("SELECT journal.record_denial(gen_random_uuid(),gen_random_uuid(),'MEMBERSHIP_INVITE','MFA_REQUIRED',repeat('0',64))")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO journal_private.denials(actor_id,operation,reason_code,correlation_id,command_id) VALUES(gen_random_uuid(),'MEMBERSHIP_INVITE','MFA_REQUIRED',gen_random_uuid(),gen_random_uuid())")).isInstanceOf(SQLException.class);
        }
    }
    @Test void runtimeHasNoJournalReadWriteDeleteTruncateOrDdl() throws Exception {
        try(var c=runtime.getConnection();var s=c.createStatement()) {
            for(String query:List.of("SELECT * FROM journal.events","UPDATE journal.events SET tenant_id=gen_random_uuid()","DELETE FROM journal.events","TRUNCATE journal.events","ALTER TABLE journal.events ADD COLUMN unsafe text","SELECT * FROM journal.outbox","SELECT * FROM journal_private.denials"))
                assertThatThrownBy(()->s.execute(query)).as(query.split(" ")[0]).isInstanceOf(SQLException.class);
        }
    }
    @Test void sessionRoleCannotReadJournalOutboxOrPrivateMetadata() throws Exception {
        try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),"fiscuvia_sessions",DATABASE.sessionPassword);var s=c.createStatement()){
            for(String query:List.of("SELECT * FROM journal.events","SELECT * FROM journal.outbox","SELECT * FROM journal_private.command_context"))
                assertThatThrownBy(()->s.execute(query)).isInstanceOf(SQLException.class);}
    }
    @Test void auditViewIsNotGrantedToOwnerAndNoHttpReaderExists() throws Exception {
        try(var f=new Fixture(true)){assertThat(f.browser.get("/api/v1/audit").statusCode()).isEqualTo(403);
            assertThat(f.browser.get(ROOT+f.tenant+"/audit").statusCode()).isEqualTo(403);}
    }
    @Test void migrationRoleStillHonorsTenantFenceWhenReadingJournal() throws Exception {
        try(var a=new Fixture(false);var b=new Fixture(false,"bob")){
            a.command(UUID.randomUUID(),"normal");b.command(UUID.randomUUID(),"normal");
            assertThat(rows(a.tenant,"SELECT tenant_id FROM journal.events").stream().map(e->e.get("tenant_id")).toList()).containsOnly(a.tenant);
            assertThat(rows(b.tenant,"SELECT tenant_id FROM journal.outbox").stream().map(e->e.get("tenant_id")).toList()).containsOnly(b.tenant);
            assertThat(global("SELECT * FROM journal.events")).isEmpty();}
    }
    @Test void payloadAllowlistRejectsSyntheticRfcAndSecrets(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        try(var f=new Fixture(false)){f.command(UUID.randomUUID(),"normal");String canary="DEMO900101XX1-private-outbox-token";
            assertThatThrownBy(()->admin(f.tenant,s->s.update("INSERT INTO journal.outbox(tenant_id,event_id,occurred_at,correlation_id,causation_id,deduplication_key,payload) SELECT tenant_id,event_id,occurred_at,correlation_id,event_id,event_id,jsonb_build_object('auditEventId',event_id::text,'secret',?) FROM journal.events",canary)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(error->assertThat(sqlState(error)).isEqualTo("23514"));
            assertThat(rows(f.tenant,"SELECT payload FROM journal.outbox").toString()).doesNotContain(canary,"secret");
            assertThat(output.getAll()).doesNotContain(canary,OIDC.password,OIDC.clientSecret,DATABASE.actorKey,DATABASE.sessionPassword);}
    }
    @Test void twoConsumersRaceForSingleEventWithoutDuplicateClaim() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant);var executor=Executors.newVirtualThreadPerTaskExecutor()){
            f.command(UUID.randomUUID(),"normal");var a=executor.submit(()->consumer.protocol.claim(1,30000));var b=executor.submit(()->consumer.protocol.claim(1,30000));
            var all=new ArrayList<OutboxProtocol.Claim>();all.addAll(a.get());all.addAll(b.get());assertThat(all).hasSize(1);
        }
    }
    @Test void committedEventSurvivesProducerExitAndConsumerRestart() throws Exception {
        UUID tenant,event;
        try(var f=new Fixture(false)){tenant=f.tenant;event=UUID.fromString(OidcBrowser.json(f.command(UUID.randomUUID(),"normal")).path("eventId").asText());}
        try(var consumer=new Consumer(tenant)){var claim=consumer.protocol.claim(1,30000).getFirst();assertThat(claim.eventId()).isEqualTo(event);
            consumer.protocol.process(claim,()->consumer.receipt(claim.eventId()));assertThat(rows(tenant,"SELECT status FROM journal.outbox").getFirst().get("status")).isEqualTo("DELIVERED");}
    }
    @Test void crashedClaimIsRecoveredAndOldLeaseCannotAcknowledge() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");var abandoned=consumer.protocol.claim(1,100).getFirst();Thread.sleep(150);
            var recovered=consumer.protocol.claim(1,30000).getFirst();assertThat(recovered.eventId()).isEqualTo(abandoned.eventId());assertThat(recovered.leaseToken()).isNotEqualTo(abandoned.leaseToken());
            assertThat(consumer.protocol.retry(abandoned,OutboxProtocol.Failure.RETRYABLE)).isFalse();
            assertThatThrownBy(()->consumer.protocol.process(abandoned,()->consumer.receipt(abandoned.eventId()))).isInstanceOf(ApiFailure.class);
            consumer.protocol.process(recovered,()->consumer.receipt(recovered.eventId()));
        }
    }
    @Test void retryBackoffIsBoundedAndNotImmediatelyClaimable() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,30000).getFirst();
            assertThat(consumer.protocol.retry(claim,OutboxProtocol.Failure.RETRYABLE)).isTrue();
            assertThat(consumer.protocol.claim(1,30000)).isEmpty();
            double delay=((Number)rows(f.tenant,"SELECT extract(epoch FROM available_at-clock_timestamp()) AS delay FROM journal.outbox").getFirst().get("delay")).doubleValue();
            assertThat(delay).isBetween(0.0,1.1);
        }
    }
    @Test void exhaustionAfterRepeatedCrashesIsTerminalAndRetainsEvent() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");for(int i=1;i<=5;i++){
                var claim=consumer.protocol.claim(1,100).getFirst();assertThat(claim.attempt()).isEqualTo(i);Thread.sleep(150);
            }
            assertThat(consumer.protocol.claim(1,100)).isEmpty();var row=rows(f.tenant,"SELECT * FROM journal.outbox").getFirst();
            assertThat(row.get("status")).isEqualTo("DEAD");assertThat(row.get("failure_code")).isEqualTo("LEASE_EXHAUSTED");assertThat(rows(f.tenant,"SELECT * FROM journal.events")).hasSize(1);
        }
    }
    @Test void effectAndAckRollbackTogetherOnConsumerFailureAndReplayIsIdempotent() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,30000).getFirst();
            assertThatThrownBy(()->consumer.protocol.process(claim,()->{consumer.receipt(claim.eventId());throw new IllegalStateException("SYNTHETIC failure");})).isInstanceOf(IllegalStateException.class);
            assertThat(global("SELECT * FROM journal_fixture.receipts WHERE event_id='"+claim.eventId()+"'")).isEmpty();
            consumer.protocol.process(claim,()->consumer.receipt(claim.eventId()));
            assertThatThrownBy(()->consumer.protocol.process(claim,()->consumer.receipt(claim.eventId()))).isInstanceOf(ApiFailure.class);
            assertThat(global("SELECT * FROM journal_fixture.receipts WHERE event_id='"+claim.eventId()+"'")).hasSize(1);
        }
    }
    @Test void effectBeforeAckCrashRequiresDownstreamIdempotency() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,100).getFirst();
            // Simulates an external effect already committed independently; no exactly-once claim.
            admin(null,s->s.update("INSERT INTO journal_fixture.receipts VALUES(?)",claim.eventId()));Thread.sleep(150);
            var recovered=consumer.protocol.claim(1,30000).getFirst();consumer.protocol.process(recovered,()->consumer.receipt(recovered.eventId()));
            assertThat(global("SELECT * FROM journal_fixture.receipts WHERE event_id='"+claim.eventId()+"'")).hasSize(1);
        }
    }
    @Test void transportAuthorityCannotReadBusinessOrOtherTenantOrAudit() throws Exception {
        try(var a=new Fixture(false);var b=new Fixture(false,"bob");var consumer=new Consumer(a.tenant);var other=new Consumer(b.tenant)){
            a.command(UUID.randomUUID(),"normal");b.command(UUID.randomUUID(),"normal");var claims=consumer.protocol.claim(100,30000);
            assertThat(claims).hasSize(1);assertThat(claims.getFirst().tenantId()).isEqualTo(a.tenant);
            var foreign=other.protocol.claim(1,30000).getFirst();
            assertThatThrownBy(()->consumer.protocol.process(foreign,()->{throw new AssertionError("Cross-tenant effect");})).isInstanceOf(ApiFailure.class);
            for(String query:List.of("SELECT * FROM app.tenants","SELECT * FROM identity.memberships","SELECT * FROM journal.events","SELECT * FROM journal.outbox","SELECT app.establish_tenant_context('"+b.tenant+"')"))
                assertThatThrownBy(()->consumer.sql.execute(query)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void runtimeAndUnconfiguredTransportFailClosedForClaims() throws Exception {
        try(var c=runtime.getConnection();var s=c.createStatement()) {assertThatThrownBy(()->s.execute("SELECT * FROM journal.claim(1,30000)")).isInstanceOf(SQLException.class);}
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            admin(null,s->s.update("UPDATE journal_private.delivery_scopes SET active=false WHERE role_oid=?",consumer.roleOid));
            assertThatThrownBy(()->consumer.protocol.claim(1,30000)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void unsupportedVersionIsDeadWithoutExecutingEffect() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");
            // Controlled future-schema fixture; keep FORCE RLS/tenant fence intact.
            admin(f.tenant,s->{s.execute("ALTER TABLE journal.outbox DISABLE TRIGGER immutable_intent");s.update("UPDATE journal.outbox SET schema_version=2");s.execute("ALTER TABLE journal.outbox ENABLE TRIGGER immutable_intent");});
            var claim=consumer.protocol.claim(1,30000).getFirst();consumer.protocol.process(claim,()->{throw new AssertionError("Unsupported payload executed");});
            assertThat(rows(f.tenant,"SELECT status,failure_code FROM journal.outbox").getFirst()).containsEntry("status","DEAD").containsEntry("failure_code","UNSUPPORTED_VERSION");
        }
    }
    @Test void unknownPayloadTypeAndVersionZeroAreRejected() throws Exception {
        try(var f=new Fixture(false)){f.command(UUID.randomUUID(),"normal");
            assertThatThrownBy(()->admin(f.tenant,s->s.update("UPDATE journal.outbox SET event_type='UNAPPROVED_TYPE'"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->admin(f.tenant,s->s.update("UPDATE journal.outbox SET schema_version=0"))).isInstanceOf(org.springframework.dao.DataAccessException.class);}
    }
    @Test void queueOrderingAndSkipLockedAvoidHeadOfLineBlocking() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");f.command(UUID.randomUUID(),"normal");
            var first=consumer.protocol.claim(1,30000).getFirst();var second=consumer.protocol.claim(1,30000).getFirst();assertThat(second.eventId()).isNotEqualTo(first.eventId());
            assertThat(consumer.protocol.claim(1,30000)).isEmpty();
        }
    }
    @Test void noSimultaneousProcessingWhileFencedCallbackIsRunning() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant);var executor=Executors.newVirtualThreadPerTaskExecutor()){
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,30000).getFirst();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var first=executor.submit(()->consumer.protocol.process(claim,()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return consumer.receipt(claim.eventId());}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();var second=executor.submit(()->consumer.protocol.process(claim,()->consumer.receipt(claim.eventId())));
            try {assertThat(consumer.protocol.claim(1,30000)).isEmpty();release.countDown();first.get();assertThatThrownBy(second::get).hasCauseInstanceOf(ApiFailure.class);}
            finally {release.countDown();}
            assertThat(global("SELECT * FROM journal_fixture.receipts WHERE event_id='"+claim.eventId()+"'")).hasSize(1);
        }
    }
    @Test void expiredLeaseRollsBackLocalEffectEvenWithoutACompetingWorker() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,100).getFirst();
            assertThatThrownBy(()->consumer.protocol.process(claim,()->{consumer.receipt(claim.eventId());try{Thread.sleep(150);}catch(InterruptedException e){throw new IllegalStateException(e);}return null;})).isInstanceOf(ApiFailure.class);
            assertThat(global("SELECT * FROM journal_fixture.receipts WHERE event_id='"+claim.eventId()+"'")).isEmpty();
        }
    }
    @Test void changingMembershipBeforeCommandDeniesAndCannotWriteSuccess() throws Exception {
        try(var f=new Fixture(false)){
            admin(f.tenant,s->s.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",f.membership));
            assertThat(f.command(UUID.randomUUID(),"normal").statusCode()).isEqualTo(403);assertThat(value(f)).isZero();
            assertThat(rows(f.tenant,"SELECT * FROM journal.events WHERE event_code='AUTHORIZED_OPERATION_RECORDED'")).isEmpty();
            try(var transport=new Consumer(f.tenant)){
                var notice=transport.protocol.claim(1,30000).getFirst();transport.protocol.process(notice,()->null);
                assertThat(rows(f.tenant,"SELECT status FROM journal.outbox").getFirst().get("status")).isEqualTo("DELIVERED");
            }
        }
    }
    @Test void reusedConnectionCannotReadOrDeliverAnotherTenantsEvent() throws Exception {
        try(var a=new Fixture(false);var b=new Fixture(false,"bob")){
            a.command(UUID.randomUUID(),"normal");b.command(UUID.randomUUID(),"normal");
            try(var c=migration()){
                for(UUID tenant:List.of(a.tenant,b.tenant,a.tenant,b.tenant)){
                    c.setAutoCommit(false);var s=new JdbcTemplate(new SingleConnectionDataSource(c,true));s.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
                    assertThat(s.queryForList("SELECT tenant_id FROM journal.events",UUID.class)).containsOnly(tenant);c.commit();
                }
            }
        }
    }
    @Test void appendOnlyTriggerResistsAccidentalMigratorUpdatesAndDeletes() throws Exception {
        try(var f=new Fixture(false)){f.command(UUID.randomUUID(),"normal");
            for(String query:List.of("UPDATE journal.events SET resource_id=gen_random_uuid()","DELETE FROM journal.events","TRUNCATE journal.events"))
                assertThatThrownBy(()->admin(f.tenant,s->s.execute(query))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(rows(f.tenant,"SELECT * FROM journal.events")).hasSize(1);}
    }
    @Test void journalGuardDetectsExcessiveGrants() throws Exception {
        var guard=context.getBean("verifyJournal",org.springframework.beans.factory.InitializingBean.class);
        admin(null,s->s.execute("GRANT UPDATE ON journal.events TO fiscuvia_runtime"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(StartupGuardFailure.class);}
        finally {admin(null,s->s.execute("REVOKE UPDATE ON journal.events FROM fiscuvia_runtime"));}guard.afterPropertiesSet();
    }
    @Test void journalGuardDetectsRemovedFenceOrAppendOnlyTrigger() throws Exception {
        var guard=context.getBean("verifyJournal",org.springframework.beans.factory.InitializingBean.class);
        admin(null,s->s.execute("ALTER TABLE journal.events DISABLE TRIGGER append_only"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(StartupGuardFailure.class);}
        finally {admin(null,s->s.execute("ALTER TABLE journal.events ENABLE TRIGGER append_only"));}
        admin(null,s->s.execute("DROP POLICY tenant_fence ON journal.events"));
        try {assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(StartupGuardFailure.class);}
        finally {admin(null,s->s.execute("CREATE POLICY tenant_fence ON journal.events AS RESTRICTIVE TO fiscuvia_migrator USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id())"));}guard.afterPropertiesSet();
    }
    @Test void removalOfJournalRlsFenceIsDetectedByRealRowOracle() throws Exception {
        try(var a=new Fixture(false);var b=new Fixture(false,"bob")){
            a.command(UUID.randomUUID(),"normal");b.command(UUID.randomUUID(),"normal");
            assertFence(a.tenant);admin(null,s->s.execute("DROP POLICY tenant_fence ON journal.events"));
            try{assertThatThrownBy(()->assertFence(a.tenant)).isInstanceOf(AssertionError.class);}
            finally{admin(null,s->s.execute("CREATE POLICY tenant_fence ON journal.events AS RESTRICTIVE TO fiscuvia_migrator USING(tenant_id=app.current_tenant_id()) WITH CHECK(tenant_id=app.current_tenant_id())"));}assertFence(a.tenant);
        }
    }
    void assertFence(UUID tenant) throws Exception {assertThat(rows(tenant,"SELECT tenant_id FROM journal.events").stream().map(e->e.get("tenant_id")).toList()).containsOnly(tenant);}
    @Test void outboxIntentAndTerminalHistoryCannotBeChanged() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            f.command(UUID.randomUUID(),"normal");assertThatThrownBy(()->admin(f.tenant,s->s.update("UPDATE journal.outbox SET tenant_id=gen_random_uuid()"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            var claim=consumer.protocol.claim(1,30000).getFirst();consumer.protocol.process(claim,()->null);
            assertThatThrownBy(()->admin(f.tenant,s->s.update("UPDATE journal.outbox SET status='PENDING'"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void claimBoundsAndPermanentFailureAreEnforced() throws Exception {
        try(var f=new Fixture(false);var consumer=new Consumer(f.tenant)){
            assertThatThrownBy(()->consumer.protocol.claim(101,100)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->consumer.protocol.claim(1,0)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            f.command(UUID.randomUUID(),"normal");var claim=consumer.protocol.claim(1,30000).getFirst();
            assertThat(consumer.protocol.retry(claim,OutboxProtocol.Failure.PERMANENT)).isTrue();assertThat(consumer.protocol.claim(1,30000)).isEmpty();
        }
    }
    @Test void failedCommandCanBeRetriedAfterRollback() throws Exception {
        try(var f=new Fixture(false)){UUID command=UUID.randomUUID();assertThat(f.command(command,"after").statusCode()).isEqualTo(409);
            assertThat(f.command(command,"normal").statusCode()).isEqualTo(200);assertThat(value(f)).isEqualTo(1);}
    }
    @Test void journalCannotRunOutsideAnAuthorizedTransaction() {
        assertThatThrownBy(()->journal.execute(UUID.randomUUID(),()->{throw new AssertionError("Unauthorized effect");})).isInstanceOf(ApiFailure.class);
    }
    @Test void nestedPrivilegedDenialIsRecordedOnlyAfterOuterRollback() throws Exception {
        try(var f=new Fixture(false)){
            assertThat(f.command(UUID.randomUUID(),"nested-denial").statusCode()).isEqualTo(403);
            assertThat(value(f)).isZero();assertThat(rows(f.tenant,"SELECT * FROM journal.events")).isEmpty();
            assertThat(global("SELECT * FROM journal_private.denials WHERE actor_id='"+f.user+"'")).hasSize(1);
        }
    }
    @Test void revocationDuringAuditedCommandWaitsForCommitThenBlocksNextOperation() throws Exception {
        try(var f=new Fixture(false);var executor=Executors.newVirtualThreadPerTaskExecutor()){
            JournalFixtureController.entered=new CountDownLatch(1);JournalFixtureController.release=new CountDownLatch(1);
            var operation=executor.submit(()->f.command(UUID.randomUUID(),"hold"));
            try {
                assertThat(JournalFixtureController.entered.await(10,TimeUnit.SECONDS)).isTrue();
                var revoke=executor.submit(()->{admin(f.tenant,s->s.update("UPDATE identity.memberships SET status='REVOKED' WHERE id=?",f.membership));return true;});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean blocked=false;
                while(System.nanoTime()<deadline){
                    if(Boolean.TRUE.equals(global("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.usename='fiscuvia_migrator' AND NOT l.granted) AS blocked").getFirst().get("blocked"))){blocked=true;break;}
                    Thread.sleep(20);
                }
                assertThat(blocked).isTrue();JournalFixtureController.release.countDown();assertThat(operation.get().statusCode()).isEqualTo(200);assertThat(revoke.get()).isTrue();
                assertThat(f.command(UUID.randomUUID(),"normal").statusCode()).isEqualTo(403);
                assertThat(rows(f.tenant,"SELECT event_code FROM journal.events ORDER BY occurred_at").stream().map(e->e.get("event_code")).toList())
                    .containsExactly("AUTHORIZED_OPERATION_RECORDED","MEMBERSHIP_REVOKED");assertThat(value(f)).isEqualTo(1);
            }finally{JournalFixtureController.release.countDown();}
        }
    }

    static final class Fixture implements AutoCloseable {
        static final java.util.concurrent.atomic.AtomicInteger ACTORS=new java.util.concurrent.atomic.AtomicInteger();
        final OidcBrowser browser=new OidcBrowser(TLS,OIDC);final UUID tenant=UUID.randomUUID(),user,membership,approval=UUID.randomUUID();
        Fixture(boolean owner) throws Exception {this(owner,List.of("alice","bob","carol").get(Math.floorMod(ACTORS.getAndIncrement(),3)));}
        Fixture(boolean owner,String login) throws Exception {
            user=UUID.fromString(browser.login(login).path("userId").asText());UUID[] member=new UUID[1];
            admin(tenant,s->{s.update("INSERT INTO app.tenants(tenant_id) VALUES(?)",tenant);
                if(owner){s.queryForObject("SELECT identity_private.bootstrap_owner(?,?,?)",Object.class,tenant,user,approval);
                    member[0]=s.queryForObject("SELECT id FROM identity.memberships WHERE user_id=?",UUID.class,user);
                } else {member[0]=UUID.randomUUID();s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,member[0],user);
                    s.update("INSERT INTO identity.membership_roles VALUES(?,?,'ACCOUNTANT')",tenant,member[0]);}
                s.update("INSERT INTO journal_fixture.counters(tenant_id,id) VALUES(?,?)",tenant,tenant);});membership=member[0];
        }
        java.net.http.HttpResponse<String> command(UUID id,String mode) throws Exception {return browser.send("POST",ROOT+tenant+"/command/"+id+"?mode="+mode,null,browser.csrf());}
        java.net.http.HttpResponse<String> status(UUID id,String state) throws Exception {return browser.send("POST","/api/v1/auth/memberships/status","{\"tenantId\":\""+tenant+"\",\"membershipId\":\""+id+"\",\"status\":\""+state+"\"}",browser.csrf());}
        tools.jackson.databind.JsonNode invite(OidcBrowser recipient) throws Exception {var r=browser.send("POST","/api/v1/auth/invitations","{\"tenantId\":\""+tenant+"\",\"subject\":\""+KeycloakTestProvider.subject(recipient.username)+"\",\"role\":\"ACCOUNTANT\"}",browser.csrf());assertThat(r.statusCode()).isEqualTo(201);return OidcBrowser.json(r);}
        public void close(){browser.close();}
    }
    static final class Consumer implements AutoCloseable {
        final com.zaxxer.hikari.HikariDataSource source=new com.zaxxer.hikari.HikariDataSource();final JdbcTemplate sql;final OutboxProtocol protocol;final long roleOid;
        Consumer(UUID tenant) throws Exception {
            String role="delivery_"+UUID.randomUUID().toString().replace("-",""),password=UUID.randomUUID().toString();
            try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var s=c.createStatement()){
                s.execute("CREATE ROLE "+role+" LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '"+password+"'");
                s.execute("GRANT CONNECT ON DATABASE fiscuvia_local TO "+role);
            }
            var found=global("SELECT oid FROM pg_roles WHERE rolname='"+role+"'");roleOid=((Number)found.getFirst().get("oid")).longValue();
            admin(null,s->{s.update("INSERT INTO journal_private.delivery_scopes VALUES(?::oid,?,?,true)",roleOid,tenant,UUID.randomUUID());
                s.execute("GRANT USAGE ON SCHEMA journal,journal_fixture TO "+role);
                s.execute("GRANT EXECUTE ON FUNCTION journal.claim(integer,integer),journal.lock_delivery(uuid,uuid),journal.read_delivery(uuid,uuid),journal.complete_delivery(uuid,uuid,text) TO "+role);
                s.execute("GRANT SELECT,INSERT ON journal_fixture.receipts TO "+role);});
            source.setJdbcUrl(DATABASE.getJdbcUrl());source.setUsername(role);source.setPassword(password);source.setMaximumPoolSize(3);source.setMinimumIdle(0);source.setConnectionTimeout(3000);
            sql=new JdbcTemplate(source);protocol=OutboxProtocols.postgres(source,new org.springframework.jdbc.support.JdbcTransactionManager(source));
        }
        int receipt(UUID event){return sql.update("INSERT INTO journal_fixture.receipts VALUES(?) ON CONFLICT DO NOTHING",event);}
        public void close(){source.close();}
    }
    static Connection migration() throws Exception{return DriverManager.getConnection(DATABASE.getJdbcUrl(),PostgresTestDatabase.MIGRATION_USER,DATABASE.migrationPassword);}
    static String sqlState(Throwable error){while(!(error instanceof SQLException)&&error.getCause()!=null)error=error.getCause();return error instanceof SQLException sql?sql.getSQLState():"";}
    static void admin(UUID tenant,java.util.function.Consumer<JdbcTemplate> work) throws Exception {try(var c=migration()){
        c.setAutoCommit(false);var sql=new JdbcTemplate(new SingleConnectionDataSource(c,true));if(tenant!=null)sql.queryForObject("SELECT app.establish_tenant_context(?)",Object.class,tenant);
        work.accept(sql);c.commit();}}
    static List<Map<String,Object>> rows(UUID tenant,String query) throws Exception {var result=new ArrayList<Map<String,Object>>();admin(tenant,s->result.addAll(s.queryForList(query)));return result;}
    static List<Map<String,Object>> global(String query) throws Exception {return rows(null,query);}
    static int value(Fixture f) throws Exception {return ((Number)rows(f.tenant,"SELECT value FROM journal_fixture.counters").getFirst().get("value")).intValue();}
    static UUID member(UUID tenant,UUID user) throws Exception {UUID id=UUID.randomUUID();admin(tenant,s->{s.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,id,user);s.update("INSERT INTO identity.membership_roles VALUES(?,?,'ACCOUNTANT')",tenant,id);});return id;}
    @RestController static class JournalFixtureController {
        static volatile CountDownLatch entered,release;
        final AuthorizedTenantOperations operations;final AuditJournal journal;final JdbcTemplate sql;
        JournalFixtureController(AuthorizedTenantOperations operations,AuditJournal journal,DataSource data){this.operations=operations;this.journal=journal;sql=new JdbcTemplate(data);}
        @PostMapping(ROOT+"{tenant}/command/{command}") AuditJournal.Receipt command(@PathVariable UUID tenant,@PathVariable UUID command,@RequestParam(defaultValue="normal") String mode){
            return operations.execute(tenant,Capability.TENANT_READ,ResourceTarget.tenant(),scope->{
                var receipt=journal.execute(command,()->{if(mode.equals("before"))throw new ApiFailure(ApiErrorCode.CONFLICT);
                    sql.update("UPDATE journal_fixture.counters SET value=value+1");if(mode.equals("after"))throw new ApiFailure(ApiErrorCode.CONFLICT);
                    if(mode.equals("hold")){entered.countDown();try{if(!release.await(12,TimeUnit.SECONDS))throw new ApiFailure(ApiErrorCode.CONFLICT);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiFailure(ApiErrorCode.CONFLICT);}}
                });
                if(mode.equals("after-journal"))throw new ApiFailure(ApiErrorCode.CONFLICT);
                if(mode.equals("nested-denial"))operations.execute(tenant,Capability.MEMBERSHIP_INVITE,ResourceTarget.tenant(),a->null);
                return receipt;
            });
        }
        @GetMapping(ROOT+"{tenant}/audit") String audit(@PathVariable UUID tenant){return operations.execute(tenant,Capability.AUDIT_VIEW,ResourceTarget.collection(ResourceTarget.Kind.AUDIT),s->"unreachable");}
    }
}
