package com.fiscuvia;

import java.sql.DriverManager;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresFoundationTests {

    @Container
    static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @Autowired
    private DataSource dataSource;
    @Autowired
    private Flyway flyway;
    @Autowired
    private Environment environment;

    @Test
    void connectsAsRestrictedRuntimeToRealPostgres18() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            try (var result = statement.executeQuery("SELECT current_user, current_schema(), version()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(PostgresTestDatabase.RUNTIME_USER);
                assertThat(result.getString(2)).isEqualTo("app");
                assertThat(result.getString(3)).contains("18.6");
            }
            assertThatThrownBy(() -> statement.execute("CREATE TABLE app.not_allowed(id integer)"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM infrastructure.flyway_schema_history"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("permission denied");
            try (var result = statement.executeQuery("SELECT rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname=current_user")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    void migratesOnlyInfrastructureOnceAndPreservesHistory() throws Exception {
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), PostgresTestDatabase.MIGRATION_USER, DATABASE.migrationPassword);
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*), min(checksum), bool_and(success) FROM infrastructure.flyway_schema_history WHERE version='1'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
                assertThat(result.getObject(2)).isNotNull();
                assertThat(result.getBoolean(3)).isTrue();
            }
            try (var result = statement.executeQuery("SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')")) {
                var tables = new java.util.ArrayList<String>();
                while (result.next()) tables.add(result.getString(1));
                assertThat(tables).containsExactlyInAnyOrder("infrastructure.flyway_schema_history",
                        "infrastructure.rls_transaction_context", "app.tenants", "identity_private.proof_key",
                        "identity_private.users", "identity_private.external_identities", "identity_private.actor_context",
                        "identity_private.owner_approvals", "identity_private.membership_events", "identity.memberships", "identity.membership_roles", "identity.invitations",
                        "auth_sessions.spring_session", "auth_sessions.spring_session_attributes",
                        "access_control.resource_grants","access_control_private.role_capabilities","access_control_private.operation_context",
                        "journal.events","journal.outbox","journal_private.command_context","journal_private.commands",
                        "journal_private.delivery_scopes","journal_private.denials",
                        "entitlement_private.limits","entitlement_private.changes","entitlement_private.reservations","tenant_private.profiles","tenant_private.admin_commands","tenant_private.profile_commands");
            }
            try (var result = statement.executeQuery("SELECT count(*), bool_and(success AND checksum IS NOT NULL) FROM infrastructure.flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(7);
                assertThat(result.getBoolean(2)).isTrue();
            }
            try (var result = statement.executeQuery("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='app'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(PostgresTestDatabase.MIGRATION_USER);
            }
        }
        assertThatThrownBy(flyway::clean).hasMessageContaining("cleanDisabled");
    }

    @Test
    void testsUseTheirOwnConfigurationAndNoEmbeddedDatabase() throws Exception {
        assertThat(environment.getActiveProfiles()).containsExactly("test");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(DATABASE.runtimeUrl());
        assertThat(environment.getProperty("spring.datasource.username")).isNotEqualTo(environment.getProperty("spring.flyway.user"));
        for (String type : new String[] {"org.h2.Driver", "org.hsqldb.jdbc.JDBCDriver", "org.apache.derby.jdbc.EmbeddedDriver"}) {
            assertThatThrownBy(() -> Class.forName(type)).isInstanceOf(ClassNotFoundException.class);
        }
        try (var stream = getClass().getResourceAsStream("/test-infra/compose.yaml")) {
            assertThat(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).contains(PostgresTestDatabase.IMAGE);
        }
        assertThat(DATABASE.getContainerInfo().getHostConfig().getPortBindings().getBindings().get(new com.github.dockerjava.api.model.ExposedPort(5432)))
                .isNotEmpty().allSatisfy(binding -> assertThat(binding.getHostIp()).isEqualTo("127.0.0.1"));
        String postgresLogs = DATABASE.getLogs();
        if (!postgresLogs.equals(DATABASE.redact(postgresLogs))) {
            throw new AssertionError("A credential appeared in PostgreSQL logs.");
        }
    }
}
