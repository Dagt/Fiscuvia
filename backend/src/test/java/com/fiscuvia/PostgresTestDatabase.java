package com.fiscuvia;

import java.util.UUID;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL, shared bootstrap script, random disposable credentials and loopback-only port. */
final class PostgresTestDatabase extends PostgreSQLContainer {

    static final String IMAGE = "postgres:18.6-alpine3.24@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5";
    static final String RUNTIME_USER = "fiscuvia_runtime";
    static final String MIGRATION_USER = "fiscuvia_migrator";
    final String runtimePassword = UUID.randomUUID().toString();
    final String migrationPassword = UUID.randomUUID().toString();

    PostgresTestDatabase() {
        // Testcontainers needs explicit family metadata for a tag + digest reference.
        super(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"));
        withDatabaseName("fiscuvia_local");
        withUsername("fiscuvia_bootstrap");
        withPassword(UUID.randomUUID().toString());
        withEnv("DB_RUNTIME_PASSWORD", runtimePassword);
        withEnv("DB_MIGRATION_PASSWORD", migrationPassword);
        withCopyFileToContainer(MountableFile.forClasspathResource("test-infra/postgres/init/10-create-roles.sh"),
                "/docker-entrypoint-initdb.d/10-create-roles.sh");
        withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))));
        withReuse(false);
        withCommand("postgres", "-c", "log_min_messages=log", "-c", "log_min_error_statement=panic",
                "-c", "log_error_verbosity=terse", "-c", "log_statement=none",
                "-c", "log_parameter_max_length=0", "-c", "log_parameter_max_length_on_error=0");
    }

    void register(DynamicPropertyRegistry registry) {
        // Exercise the test YAML mapping, never the local .env or local port.
        registry.add("TEST_DB_JDBC_URL", this::runtimeUrl);
        registry.add("TEST_DB_RUNTIME_PASSWORD", () -> runtimePassword);
        registry.add("TEST_DB_MIGRATION_PASSWORD", () -> migrationPassword);
    }

    String runtimeUrl() {
        return getJdbcUrl() + "&currentSchema=app&connectTimeout=3";
    }

    String redact(String text) {
        return text.replace(runtimePassword, "[redacted]").replace(migrationPassword, "[redacted]")
                .replace(getPassword(), "[redacted]");
    }
}
