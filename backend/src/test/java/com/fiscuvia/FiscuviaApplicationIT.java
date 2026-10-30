package com.fiscuvia;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the repackaged JAR against disposable PostgreSQL, without the test classpath. */
@Timeout(60)
@Testcontainers
class FiscuviaApplicationIT {

    @Container
    static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();

    @TempDir
    Path temporaryDirectory;

    @Test
    void executableJarStartsWithDefaultLocalProfileAndServesHealth() throws Exception {
        Path log = temporaryDirectory.resolve("local-startup.log");
        Process process = start(log, "--server.port=0");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            var portPattern = Pattern.compile("Tomcat started on port (\\d+)");
            while (System.nanoTime() < deadline && process.isAlive()) {
                String output = Files.readString(log);
                assertNoCredentials(output);
                var matcher = portPattern.matcher(output);
                if (matcher.find() && output.contains("Started FiscuviaApplication")) {
                    var request = HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + matcher.group(1) + "/actuator/health"))
                            .timeout(Duration.ofSeconds(5)).GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).contains("\"status\":\"UP\"");
                    assertThat(output).contains("falling back to 1 default profile: \"local\"");
                    return;
                }
                Thread.sleep(100);
            }
            throw new AssertionError("Executable JAR did not become healthy:\n" + DATABASE.redact(Files.readString(log)));
        } finally {
            stop(process);
        }
    }

    @Test
    void executableJarRejectsAnUnsupportedEnvironment() throws Exception {
        assertStartupFailure("unsupported-profile", "supports only local/test profiles",
                "--spring.profiles.active=prod", "--server.port=0");
    }

    @Test
    void executableJarRejectsMalformedConfiguration() throws Exception {
        assertStartupFailure("invalid-port", "server.port", "--server.port=invalid");
    }

    @Test
    void executableJarRejectsUnavailablePostgres() throws Exception {
        assertStartupFailure("unavailable-db", "Connection", "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/fiscuvia_local?connectTimeout=1",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsInvalidRuntimeCredentials() throws Exception {
        assertStartupFailure("runtime-password", "authentication failed", "--spring.datasource.password=incorrect-test-only-value",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsInvalidMigrationCredentials() throws Exception {
        assertStartupFailure("migration-password", "authentication failed", "--spring.flyway.password=incorrect-test-only-value",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsAdministrativeRuntimeRole() throws Exception {
        assertStartupFailure("runtime-admin", "must not have administrative privileges",
                "--spring.datasource.username=" + DATABASE.getUsername(), "--server.port=0");
    }

    @Test
    void executableJarRejectsRemoteTargetBeforeAttemptingToConnect() throws Exception {
        assertStartupFailure("remote-url", "must target loopback/fiscuvia_local",
                "--spring.datasource.url=jdbc:postgresql://remote.example.test/fiscuvia_local", "--server.port=0");
    }

    private void assertStartupFailure(String name, String expectedDiagnostic, String... arguments)
            throws Exception {
        Path log = temporaryDirectory.resolve(name + ".log");
        Process process = start(log, arguments);
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("JAR should fail promptly").isTrue();
            assertThat(process.exitValue()).isNotZero();
            String output = Files.readString(log);
            assertNoCredentials(output);
            assertThat(DATABASE.redact(output)).contains(expectedDiagnostic);
        } finally {
            stop(process);
        }
    }

    private Process start(Path log, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("application.jar")));
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        // Keep the smoke test independent of the developer's Spring environment overrides.
        builder.environment().keySet().removeIf(key -> key.startsWith("SPRING_")
                || key.startsWith("SERVER_") || key.startsWith("MANAGEMENT_") || key.startsWith("DB_"));
        builder.environment().put("DB_PORT", DATABASE.getMappedPort(5432).toString());
        builder.environment().put("DB_RUNTIME_PASSWORD", List.of(arguments).stream()
                .anyMatch(argument -> argument.startsWith("--spring.datasource.username="))
                ? DATABASE.getPassword() : DATABASE.runtimePassword);
        builder.environment().put("DB_MIGRATION_PASSWORD", DATABASE.migrationPassword);
        return builder.start();
    }

    private void assertNoCredentials(String output) {
        if (output.contains(DATABASE.runtimePassword) || output.contains(DATABASE.migrationPassword)
                || output.contains(DATABASE.getPassword())) {
            throw new AssertionError("A credential appeared in the startup log.");
        }
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
