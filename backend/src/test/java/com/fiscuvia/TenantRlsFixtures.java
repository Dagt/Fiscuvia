package com.fiscuvia;

import java.sql.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Migrator fixtures still establish context and obey FORCE RLS. No BYPASSRLS, role switching or disabling RLS. */
final class TenantRlsFixtures {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), recordA = UUID.randomUUID(), recordB = UUID.randomUUID();
    final PostgresTestDatabase database;
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
            sql.update("INSERT INTO rls_fixture.records VALUES (?,?,?,?)", tenant, record, "DEMO900101XX1", "DEMO fixture");
            connection.commit();
        }
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
