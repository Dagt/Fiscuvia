package com.fiscuvia;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TenantMigrationIT {
    @Container final PostgresTestDatabase database = new PostgresTestDatabase();
    @TempDir Path migrations;
    @Test void upgradesExistingV1AndReappliesNothingWhilePreservingItsChecksum() throws Exception {
        copy("V1__initialize_infrastructure.sql");
        flyway().migrate(); int before = checksum("1");
        copy("V2__tenant_isolation_foundation.sql");
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        flyway().validate(); assertThat(flyway().migrate().migrationsExecuted).isZero();
        assertThat(checksum("1")).isEqualTo(before);
        assertThat(checksum("2")).isNotZero();
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.RUNTIME_USER, database.runtimePassword);
                var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM app.tenants")) {
            result.next(); assertThat(result.getInt(1)).isZero();
        }
    }
    @Test void alteredV2ChecksumIsRejectedWithoutRewritingHistory() throws Exception {
        copy("V1__initialize_infrastructure.sql"); copy("V2__tenant_isolation_foundation.sql"); flyway().migrate();
        int before = checksum("2");
        Files.writeString(migrations.resolve("V2__tenant_isolation_foundation.sql"), "\n-- deliberate mutation\n", StandardOpenOption.APPEND);
        assertThatThrownBy(() -> flyway().validate()).isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        assertThat(checksum("2")).isEqualTo(before);
    }
    @Test void wholeTenancyMigrationRollsBackOnSqlFailureAndCanBeRetried() throws Exception {
        copy("V1__initialize_infrastructure.sql"); flyway().migrate(); copy("V2__tenant_isolation_foundation.sql");
        Files.writeString(migrations.resolve("V2__tenant_isolation_foundation.sql"), "\nTHIS IS NOT SQL;\n", StandardOpenOption.APPEND);
        assertThatThrownBy(() -> flyway().migrate()).isInstanceOf(FlywayException.class);
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword);
                var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT to_regclass('app.tenants'), to_regclass('infrastructure.rls_transaction_context'), to_regprocedure('app.current_tenant_id()')")) {
            rows.next(); for (int column = 1; column <= 3; column++) assertThat(rows.getObject(column)).isNull();
        }
        copy("V2__tenant_isolation_foundation.sql"); assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        flyway().validate();
    }
    private void copy(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/" + name)) { Files.writeString(migrations.resolve(name), new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
    }
    private Flyway flyway() { return Flyway.configure().dataSource(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword)
            .locations("filesystem:" + migrations).defaultSchema("infrastructure").validateOnMigrate(true).cleanDisabled(true).load(); }
    private int checksum(String version) throws Exception {
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword);
                var statement = connection.prepareStatement("SELECT checksum FROM infrastructure.flyway_schema_history WHERE version=? AND success")) {
            statement.setString(1, version); try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); return rows.getInt(1); }
        }
    }
}
