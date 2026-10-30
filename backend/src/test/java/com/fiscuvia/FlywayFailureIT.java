package com.fiscuvia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A new PostgreSQL container per test keeps deliberate corruptions isolated. */
@Testcontainers
class FlywayFailureIT {

    @Container
    final PostgresTestDatabase database = new PostgresTestDatabase();
    @TempDir
    Path migrations;

    @Test
    void refusesAnAlteredChecksumWithoutRewritingHistory() throws Exception {
        copyV1();
        var flyway = configuredFlyway();
        flyway.migrate();
        Files.writeString(migrations.resolve("V1__initialize_infrastructure.sql"), "\n-- deliberate checksum change\n", java.nio.file.StandardOpenOption.APPEND);
        assertThatThrownBy(() -> configuredFlyway().migrate()).isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum mismatch");
        assertHistoryIntact();
    }

    @Test
    void rollsBackAnInvalidSqlMigration() throws Exception {
        copyV1();
        configuredFlyway().migrate();
        Files.writeString(migrations.resolve("V2__deliberate_failure.sql"),
                "CREATE TABLE app.should_rollback(id integer);\nTHIS IS NOT SQL;\n");
        assertThatThrownBy(() -> configuredFlyway().migrate()).isInstanceOf(FlywayException.class);
        assertHistoryIntact();
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword);
                var statement = connection.createStatement(); var result = statement.executeQuery("SELECT to_regclass('app.should_rollback')")) {
            assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
        }
    }

    private void copyV1() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__initialize_infrastructure.sql")) {
            Files.writeString(migrations.resolve("V1__initialize_infrastructure.sql"), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Flyway configuredFlyway() {
        return Flyway.configure().dataSource(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword)
                .locations("filesystem:" + migrations).defaultSchema("infrastructure")
                .validateOnMigrate(true).validateMigrationNaming(true).cleanDisabled(true).load();
    }

    private void assertHistoryIntact() throws Exception {
        try (var connection = DriverManager.getConnection(database.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, database.migrationPassword);
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT version, success FROM infrastructure.flyway_schema_history WHERE version IS NOT NULL")) {
            assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("1");
            assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.next()).isFalse();
        }
    }
}
