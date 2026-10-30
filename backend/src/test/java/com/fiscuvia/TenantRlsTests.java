package com.fiscuvia;

import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import com.fiscuvia.platform.TenantTransactions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1"})
@Testcontainers @DirtiesContext
class TenantRlsTests {
    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @Autowired TenantTransactions tenants;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager manager;
    @Autowired ApplicationContext application;
    TenantRlsFixtures fixture;
    static boolean installed;
    @BeforeEach void seed() throws Exception {
        fixture = new TenantRlsFixtures(DATABASE);
        if (!installed) { fixture.install(); installed = true; }
        fixture.seed();
    }

    @Test void queriesWithoutTenantPredicatesAreIsolatedAndSameRfcIsAllowed() {
        for (UUID tenant : List.of(fixture.a, fixture.b)) tenants.execute(tenant, () -> {
            assertThat(sql.queryForList("SELECT tenant_id FROM app.tenants", UUID.class)).containsExactly(tenant);
            assertThat(sql.queryForList("SELECT tenant_id FROM rls_fixture.records", UUID.class)).containsExactly(tenant);
            assertThat(sql.queryForList("SELECT synthetic_rfc FROM rls_fixture.records", String.class)).containsExactly("DEMO900101XX1");
            assertThat(sql.queryForObject("SELECT current_user", String.class)).isEqualTo(PostgresTestDatabase.RUNTIME_USER);
            return null;
        });
    }
    @Test void noContextHasNoRowsInsideAndOutsideTransactions() {
        assertEmpty(); tx(() -> { assertEmpty(); return null; });
        denied("42501", () -> tx(() -> insert(fixture.a, UUID.randomUUID(), "DEMO no context")));
    }
    @Test void malformedAndForgedRawSettingsDoNotAuthorizeRows() {
        for (String value : List.of("", "malformed-private-canary", fixture.b.toString())) tx(() -> {
            sql.queryForObject("SELECT set_config('app.current_tenant_id', ?, true)", String.class, value);
            sql.queryForObject("SELECT set_config('app.current_tenant_transaction', pg_current_xact_id()::text, true)", String.class);
            assertEmpty(); return null;
        });
    }
    @Test void aCannotReadUpdateOrDeleteBAndOwnWritesWork() {
        tenants.execute(fixture.a, () -> {
            assertThat(sql.queryForList("SELECT id FROM rls_fixture.records WHERE id=?", UUID.class, fixture.recordB)).isEmpty();
            assertThat(sql.update("UPDATE rls_fixture.records SET label='DEMO changed' WHERE id=?", fixture.recordB)).isZero();
            assertThat(sql.update("DELETE FROM rls_fixture.records WHERE id=?", fixture.recordB)).isZero();
            assertThat(sql.update("UPDATE rls_fixture.records SET label='DEMO own' WHERE id=?", fixture.recordA)).isEqualTo(1);
            UUID id = UUID.randomUUID(); assertThat(insert(fixture.a, id, "DEMO own")).isEqualTo(1);
            assertThat(sql.update("DELETE FROM rls_fixture.records WHERE id=?", id)).isEqualTo(1);
            return null;
        });
        tenants.execute(fixture.b, () -> { assertThat(sql.queryForObject("SELECT label FROM rls_fixture.records", String.class)).isEqualTo("DEMO fixture"); return null; });
    }
    @Test void withCheckRejectsForgedInsertsAndTenantReassignment() {
        denied("42501", () -> tenants.execute(fixture.a, () -> insert(fixture.b, UUID.randomUUID(), "DEMO forged")));
        denied("42501", () -> tenants.execute(fixture.a, () -> sql.update("UPDATE rls_fixture.records SET tenant_id=? WHERE id=?", fixture.b, fixture.recordA)));
    }
    @Test void compoundForeignKeyRejectsCrossTenantAndAcceptsOwnReference() {
        tenants.execute(fixture.a, () -> {
            assertThat(sql.update("INSERT INTO rls_fixture.links VALUES (?,?,?)", fixture.a, UUID.randomUUID(), fixture.recordA)).isEqualTo(1);
            return null;
        });
        denied("23503", () -> tenants.execute(fixture.a, () -> sql.update("INSERT INTO rls_fixture.links VALUES (?,?,?)", fixture.a, UUID.randomUUID(), fixture.recordB)));
    }
    @Test void uniquenessIsTenantScopedAndPrimaryKeysAreComposite() {
        UUID shared = UUID.randomUUID();
        for (UUID tenant : List.of(fixture.a, fixture.b)) tenants.execute(tenant, () -> insert(tenant, shared, "DEMO shared"));
        denied("23505", () -> tenants.execute(fixture.a, () -> insert(fixture.a, UUID.randomUUID(), "DEMO shared")));
        assertThat(sql.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='rls_fixture'", String.class))
                .hasSize(4).allSatisfy(index -> assertThat(index).contains("(tenant_id,"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void samePhysicalConnectionDoesNotLeakAfterCommitOrRollback(boolean rollback) {
        var pid = new int[1]; UUID added = UUID.randomUUID();
        Supplier<Integer> work = () -> tenants.execute(fixture.a, () -> {
            pid[0] = pid(); int inserted = insert(fixture.a, added, "DEMO rollback");
            if (rollback) throw new IllegalStateException("synthetic rollback"); return inserted;
        });
        if (rollback) assertThatThrownBy(work::get).isInstanceOf(IllegalStateException.class); else assertThat(work.get()).isEqualTo(1);
        tx(() -> { assertThat(pid()).isEqualTo(pid[0]); assertEmpty(); return null; });
        tenants.execute(fixture.b, () -> { assertThat(pid()).isEqualTo(pid[0]); assertThat(ids()).containsExactly(fixture.recordB); return null; });
        tenants.execute(fixture.a, () -> { assertThat(ids().contains(added)).isEqualTo(!rollback); return null; });
    }
    @Test void sameTenantRequiredNestingSharesTransactionAndSwitchPoisonsOuterTransaction() {
        tenants.execute(fixture.a, () -> { int before = pid(); return tenants.execute(fixture.a, () -> { assertThat(pid()).isEqualTo(before); assertThat(ids()).containsExactly(fixture.recordA); return null; }); });
        UUID added = UUID.randomUUID();
        assertThatThrownBy(() -> tenants.execute(fixture.a, () -> {
            insert(fixture.a, added, "DEMO poisoned");
            assertThatThrownBy(() -> tenants.execute(fixture.b, () -> null)).isInstanceOf(IllegalStateException.class);
            return null;
        })).isInstanceOf(IllegalStateException.class);
        tenants.execute(fixture.a, () -> { assertThat(ids()).doesNotContain(added); return null; });
    }
    @Test void savepointNestingAndReadonlyTransactionsAreRejected() {
        tenants.execute(fixture.a, () -> {
            var nested = new TransactionTemplate(manager); nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(() -> nested.execute(status -> null)).isInstanceOf(NestedTransactionNotSupportedException.class);
            return null;
        });
        var readOnly = new TransactionTemplate(manager); readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> tenants.execute(fixture.a, () -> null)))
                .isInstanceOfAny(IllegalStateException.class, IllegalTransactionStateException.class);
    }
    @Test void joinsOuterTransactionAndContextEndsOnlyAtOuterBoundary() {
        tx(() -> {
            int before = pid(); tenants.execute(fixture.a, () -> null);
            assertThat(pid()).isEqualTo(before); assertThat(ids()).containsExactly(fixture.recordA); return null;
        });
        assertEmpty();
    }
    @Test void databaseBindingCannotBeChangedByResettingOrForgingGucs() {
        tenants.execute(fixture.a, () -> {
            sql.execute("SAVEPOINT test_after_binding");
            sql.queryForObject("SELECT set_config('app.current_tenant_id', ?, true)", String.class, fixture.b.toString());
            assertEmpty();
            sql.execute("ROLLBACK TO SAVEPOINT test_after_binding");
            assertThat(ids()).containsExactly(fixture.recordA);
            sql.queryForObject("SELECT app.establish_tenant_context(?)", Object.class, fixture.a);
            assertThat(ids()).containsExactly(fixture.recordA); return null;
        });
        denied("42501", () -> tenants.execute(fixture.a, () -> {
            sql.queryForObject("SELECT set_config('app.current_tenant_id', '', true)", String.class);
            return sql.queryForObject("SELECT app.establish_tenant_context(?)", Object.class, fixture.b);
        }));
    }
    @Test void autocommitContextAndSessionSettingsDoNotConferAccessInNextTransaction() {
        sql.queryForObject("SELECT app.establish_tenant_context(?)", Object.class, fixture.a);
        assertEmpty();
        sql.queryForObject("SELECT set_config('app.current_tenant_id', ?, false)", String.class, fixture.a.toString());
        sql.queryForObject("SELECT set_config('app.current_tenant_transaction', pg_current_xact_id()::text, false)", String.class);
        try { assertEmpty(); tenants.execute(fixture.b, () -> { assertThat(ids()).containsExactly(fixture.recordB); return null; }); assertEmpty(); }
        finally { sql.execute("RESET app.current_tenant_id; RESET app.current_tenant_transaction"); }
    }
    @Test void runtimeCannotModifyProvisioningRegistryOwnershipOrPolicies() {
        for (String query : List.of("INSERT INTO app.tenants(tenant_id) VALUES ('" + UUID.randomUUID() + "')",
                "UPDATE app.tenants SET created_at=now()", "DELETE FROM app.tenants", "TRUNCATE rls_fixture.records",
                "ALTER TABLE rls_fixture.records DISABLE ROW LEVEL SECURITY", "SET ROLE fiscuvia_migrator",
                "SELECT * FROM infrastructure.rls_transaction_context", "DELETE FROM infrastructure.rls_transaction_context"))
            denied("42501", () -> { sql.execute(query); return null; });
        denied("42501", () -> tenants.execute(fixture.a, () -> {
            sql.execute("SET LOCAL row_security=off");
            return ids();
        }));
    }
    @Test void nullInputFailsClosedAndTamperedContextRollsBackWork() {
        assertThatThrownBy(() -> tenants.execute(null, () -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tenants.execute(fixture.a, null)).isInstanceOf(IllegalStateException.class);
        UUID added = UUID.randomUUID();
        assertThatThrownBy(() -> tenants.execute(fixture.a, () -> {
            insert(fixture.a, added, "DEMO tampered");
            sql.queryForObject("SELECT set_config('app.current_tenant_id', 'malformed-private-canary', true)", String.class); return null;
        })).isInstanceOf(IllegalStateException.class);
        tenants.execute(fixture.a, () -> { assertThat(ids()).doesNotContain(added); return null; }); assertEmpty();
    }
    @Test void resistanceOracleDetectsDisabledRlsAndRelaxedFence() throws Exception {
        for (String change : List.of("ALTER TABLE rls_fixture.records DISABLE ROW LEVEL SECURITY",
                "DROP POLICY tenant_fence ON rls_fixture.records",
                "ALTER POLICY tenant_fence ON rls_fixture.records USING (true) WITH CHECK (true)")) {
            fixture.ddl(change);
            try { assertThatThrownBy(() -> tenants.execute(fixture.a, () -> { assertThat(ids()).containsExactly(fixture.recordA); return null; })).isInstanceOf(AssertionError.class); }
            finally {
                fixture.ddl("ALTER TABLE rls_fixture.records ENABLE ROW LEVEL SECURITY");
                fixture.ddl("DROP POLICY IF EXISTS tenant_fence ON rls_fixture.records; CREATE POLICY tenant_fence ON rls_fixture.records AS RESTRICTIVE TO fiscuvia_runtime, fiscuvia_migrator USING (tenant_id=(SELECT app.current_tenant_id())) WITH CHECK (tenant_id=(SELECT app.current_tenant_id()))");
            }
        }
    }
    @Test void startupGuardDetectsForceRlsDriftAndUnsafeRuntimeGrants() throws Exception {
        for (String change : List.of("ALTER TABLE app.tenants NO FORCE ROW LEVEL SECURITY", "GRANT TRUNCATE ON app.tenants TO fiscuvia_runtime")) {
            fixture.ddl(change);
            try { assertThatThrownBy(() -> application.getBean("verifyRuntimeDatabase", InitializingBean.class).afterPropertiesSet()).isInstanceOf(IllegalStateException.class); }
            finally { fixture.ddl("ALTER TABLE app.tenants FORCE ROW LEVEL SECURITY; REVOKE TRUNCATE ON app.tenants FROM fiscuvia_runtime"); }
        }
    }
    @Test void postgresLogsExcludeRejectedValuesAndRuntimeCannotEnableStatementLogging() {
        String canary = "DEMO900101XX1-private-postgres-canary";
        denied("22P02", () -> sql.queryForObject("SELECT CAST(? AS integer)", Integer.class, canary));
        denied("42501", () -> { sql.execute("SET log_statement='all'"); return null; });
        assertThat(sql.queryForObject("SHOW log_min_messages", String.class)).isEqualTo("log");
        assertThat(sql.queryForObject("SHOW log_min_error_statement", String.class)).isEqualTo("panic");
        assertThat(DATABASE.getLogs()).contains("ready to accept connections").doesNotContain(canary, "synthetic_rfc", "SELECT CAST");
    }
    @Test void catalogProvesNotNullForcedPoliciesAndNarrowDefinerFunctions() {
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('app','rls_fixture') AND c.relkind='r' AND a.attname='tenant_id'
                  AND a.attnotnull AND a.atttypid='uuid'::regtype AND c.relrowsecurity AND c.relforcerowsecurity
                """, Integer.class)).isEqualTo(3);
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('app','rls_fixture') AND NOT p.polpermissive AND p.polcmd='*'
                  AND p.polqual IS NOT NULL AND p.polwithcheck IS NOT NULL
                  AND (SELECT oid FROM pg_roles WHERE rolname=current_user)=ANY(p.polroles)
                """, Integer.class)).isEqualTo(3);
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace JOIN pg_roles r ON r.oid=p.proowner
                WHERE n.nspname='app' AND p.proname IN ('current_tenant_id','establish_tenant_context')
                  AND p.prosecdef AND NOT r.rolsuper AND NOT r.rolbypassrls AND r.rolname='fiscuvia_migrator'
                  AND p.proconfig=ARRAY['search_path=pg_catalog, pg_temp']
                  AND NOT EXISTS (SELECT 1 FROM aclexplode(p.proacl) a WHERE a.grantee=0 AND a.privilege_type='EXECUTE')
                """, Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication OR rolinherit FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
    }
    private int insert(UUID tenant, UUID id, String rfc) { return sql.update("INSERT INTO rls_fixture.records VALUES (?,?,?,?)", tenant,id,rfc,"DEMO fixture"); }
    private List<UUID> ids() { return sql.queryForList("SELECT id FROM rls_fixture.records", UUID.class); }
    private int pid() { return sql.queryForObject("SELECT pg_backend_pid()", Integer.class); }
    private void assertEmpty() { assertThat(ids()).isEmpty(); assertThat(sql.queryForObject("SELECT app.current_tenant_id()", UUID.class)).isNull(); }
    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(manager).execute(status -> work.get()); }
    private void denied(String state, Supplier<?> work) { assertThatThrownBy(work::get).satisfies(error -> TenantRlsFixtures.state(error,state)); }
}
