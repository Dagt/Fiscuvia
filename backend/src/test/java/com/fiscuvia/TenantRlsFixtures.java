package com.fiscuvia;

import java.sql.*;
import java.util.UUID;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fiscuvia.platform.TenantTransactions;

/** Migrator fixtures still establish context and obey FORCE RLS. No BYPASSRLS, role switching or disabling RLS. */
final class TenantRlsFixtures {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), recordA = UUID.randomUUID(), recordB = UUID.randomUUID();
    final PostgresTestDatabase database;
    final List<String> actors=java.util.stream.IntStream.range(0,4).mapToObj(n->UUID.randomUUID().toString()).toList();
    final java.util.concurrent.ConcurrentHashMap<Integer,String> connections=new java.util.concurrent.ConcurrentHashMap<>();
    final java.util.concurrent.atomic.AtomicInteger nextActor=new java.util.concurrent.atomic.AtomicInteger();
    TenantRlsFixtures(PostgresTestDatabase database) { this.database = database; }
    Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword);
    }
    void install() throws Exception {
        try (var connection = migrationConnection(); var statement = connection.createStatement();
                var resource = getClass().getResourceAsStream("/tenancy/rls-fixtures.sql")) {
            connection.setAutoCommit(false);
            statement.execute(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            connection.commit();
        }
    }
    void seed() throws Exception { seed(a, recordA); seed(b, recordB); }
    private void seed(UUID tenant, UUID record) throws Exception {
        try (var connection = migrationConnection()) {
            connection.setAutoCommit(false);
            var sql = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            sql.queryForObject("SELECT app.establish_tenant_context(?)", Object.class, tenant);
            sql.update("INSERT INTO app.tenants(tenant_id) VALUES (?)", tenant);
            for(String actor:actors) {
                UUID user=UUID.fromString(actor);
                sql.update("INSERT INTO identity_private.users(id) VALUES(?) ON CONFLICT DO NOTHING",user);
                sql.update("INSERT INTO identity_private.external_identities(issuer,subject,user_id) VALUES('urn:fiscuvia:rls-fixture',?,?) ON CONFLICT DO NOTHING",actor,user);
                UUID member=UUID.randomUUID();
                sql.update("INSERT INTO identity.memberships(tenant_id,id,user_id,status) VALUES(?,?,?,'ACTIVE')",tenant,member,user);
                sql.update("INSERT INTO identity.membership_roles VALUES(?,?,'ACCOUNTANT')",tenant,member);
            }
            sql.update("INSERT INTO rls_fixture.records VALUES (?,?,?,?)", tenant, record, "DEMO900101XX1", "DEMO fixture");
            connection.commit();
        }
    }
    /** Trusted INTERNAL test adapter for the pre-existing #26 SQL suites. No HTTP filter, mock or RLS bypass.
     * Authentication/cryptography/browser behavior is tested separately with actual Keycloak in #27. */
    TenantTransactions authorizedTransactions(TenantTransactions delegate,JdbcTemplate sql,org.springframework.transaction.PlatformTransactionManager manager) {
        return new TenantTransactions() {
            @Override public <T> T execute(UUID tenant,java.util.function.Supplier<T> work) {
                if(tenant==null || work==null) return delegate.execute(tenant,work);
                try {
                    return new org.springframework.transaction.support.TransactionTemplate(manager).execute(status->{
                        if(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                            throw new IllegalStateException("Read-only test unit rejected");
                        int pid=sql.queryForObject("SELECT pg_backend_pid()",Integer.class);
                        String actor=connections.computeIfAbsent(pid,key->actors.get(nextActor.getAndIncrement()));
                        String tx=sql.queryForObject("SELECT pg_current_xact_id()::text",String.class);
                        try {
                            var mac=javax.crypto.Mac.getInstance("HmacSHA256");
                            mac.init(new javax.crypto.spec.SecretKeySpec(java.util.HexFormat.of().parseHex(database.actorKey),"HmacSHA256"));
                            String proof=java.util.HexFormat.of().formatHex(mac.doFinal(("urn:fiscuvia:rls-fixture\n"+actor+"\n"+tx+"\n0").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                            sql.queryForList("SELECT * FROM identity.bind_actor(?,?,0,?)","urn:fiscuvia:rls-fixture",actor,proof);
                        } catch(java.security.GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
                        sql.queryForObject("SELECT identity.authorize_tenant(?,false)",Object.class,tenant);
                        return delegate.execute(tenant,work);
                    });
                } catch(org.springframework.dao.DataAccessException rejected) { throw new IllegalStateException("Internal fixture unit rejected",rejected); }
            }
        };
    }
    void ddl(String sql) throws Exception {
        try (var connection = migrationConnection(); var query = connection.createStatement()) { query.execute(sql); }
    }
    static void state(Throwable error, String expected) {
        Throwable current = error;
        while (!(current instanceof SQLException) && current.getCause() != null) current = current.getCause();
        org.assertj.core.api.Assertions.assertThat(current).isInstanceOfSatisfying(SQLException.class,
                sql -> org.assertj.core.api.Assertions.assertThat(sql.getSQLState()).isEqualTo(expected));
    }
}
