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
            var portPattern = Pattern.compile("\"port\":(\\d+)");
            while (System.nanoTime() < deadline && process.isAlive()) {
                String output = Files.readString(log);
                assertNoCredentials(output);
                var matcher = portPattern.matcher(output);
                if (matcher.find() && output.contains("APPLICATION_READY")) {
                    var request = HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + matcher.group(1) + "/actuator/health"))
                            .timeout(Duration.ofSeconds(5)).GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).contains("\"status\":\"UP\"");
                    assertThat(output).contains("\"event\":\"APPLICATION_READY\"");
                    var info = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                            + matcher.group(1) + "/api/v1/public/info")).GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertThat(info.statusCode()).isEqualTo(200);
                    assertThat(info.body()).contains("Fiscuvia", "1.0.0").doesNotContain("jdbc:", "testcontainers");
                    var fixture = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                            + matcher.group(1) + "/__contract-test/errors/INTERNAL_ERROR")).GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertThat(fixture.statusCode()).isEqualTo(404);
                    var me=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+matcher.group(1)+"/api/v1/me")).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertThat(me.statusCode()).isEqualTo(401);
                    assertThat(me.body()).contains("AUTHENTICATION_REQUIRED").doesNotContain("token","issuer","subject");
                    return;
                }
                Thread.sleep(100);
            }
            throw new AssertionError("Executable JAR did not become healthy:\n" + DATABASE.redact(Files.readString(log)));
        } finally {
            stop(process);
        }
    }

    @Test void executableJarContainsNoLocalIssuerFixtureUsersOrPrivateKeyMaterial() throws Exception {
        try(var archive=new java.util.zip.ZipFile(System.getProperty("application.jar"))) {
            var files=archive.stream().map(java.util.zip.ZipEntry::getName).filter(name->name.startsWith("BOOT-INF/classes/")).toList();
            assertThat(files).noneMatch(name->name.contains("KeycloakTestProvider") || name.contains("LocalOidcHarness")
                    || name.contains("OidcBrowser") || name.contains("ContractFixtureSecurity") || name.contains("FixtureController")
                    || name.contains("test-infra") || name.contains("realm-template") || name.endsWith(".p12") || name.endsWith(".pem") || name.endsWith(".key"));
            String config=new String(archive.getInputStream(archive.getEntry("BOOT-INF/classes/application.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(config).contains("${FISCUVIA_OIDC_ENABLED:false}").doesNotContain(DATABASE.actorKey,DATABASE.sessionPassword);
        }
    }

    @Test
    void executableJarRejectsAnUnsupportedEnvironment() throws Exception {
        assertStartupFailure("unsupported-profile", "CONFIGURATION_REJECTED",
                "--spring.profiles.active=prod", "--server.port=0");
    }

    @Test
    void executableJarRejectsMalformedConfiguration() throws Exception {
        assertStartupFailure("invalid-port", "CONFIGURATION_REJECTED", "--server.port=invalid");
    }

    @Test
    void executableJarRejectsUnavailablePostgres() throws Exception {
        assertStartupFailure("unavailable-db", "DATABASE_UNAVAILABLE", "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/fiscuvia_local?connectTimeout=1",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsInvalidRuntimeCredentials() throws Exception {
        assertStartupFailure("runtime-password", "DATABASE_AUTHENTICATION", "--spring.datasource.password=incorrect-test-only-value",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsInvalidMigrationCredentials() throws Exception {
        assertStartupFailure("migration-password", "DATABASE_AUTHENTICATION", "--spring.flyway.password=incorrect-test-only-value",
                "--server.port=0");
    }

    @Test
    void executableJarRejectsAdministrativeRuntimeRole() throws Exception {
        assertStartupFailure("runtime-admin", "DATABASE_PRIVILEGES",
                "--spring.datasource.username=" + DATABASE.getUsername(), "--server.port=0");
    }

    @Test
    void executableJarRejectsRemoteTargetBeforeAttemptingToConnect() throws Exception {
        assertStartupFailure("remote-url", "CONFIGURATION_REJECTED",
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
            assertThat(DATABASE.redact(output)).contains("APPLICATION_FAILED", expectedDiagnostic)
                    .doesNotContain("Exception:", "jdbc:postgresql", "incorrect-test-only-value");
        } finally {
            stop(process);
        }
    }

    private Process start(Path log, String... arguments) throws Exception {
        return start(log, java.util.Map.of(), arguments);
    }

    private Process start(Path log, java.util.Map<String, String> environment, String... arguments) throws Exception {
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
        builder.environment().put("DB_SESSION_PASSWORD", DATABASE.sessionPassword);
        builder.environment().put("FISCUVIA_ACTOR_PROOF_KEY", DATABASE.actorKey);
        builder.environment().putAll(environment);
        return builder.start();
    }

    @Test
    void executableJarDoesNotExportEvenWhenOtelEnvironmentPointsAtAnEndpoint() throws Exception {
        try (var trap = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            trap.setSoTimeout(6000);
            Path log = temporaryDirectory.resolve("no-export.log");
            var process = start(log, java.util.Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:" + trap.getLocalPort(),
                    "OTEL_TRACES_EXPORTER", "otlp", "OTEL_EXPORTER_OTLP_HEADERS", "authorization=private-export-canary"),
                    "--server.port=0", "--management.tracing.sampling.probability=1");
            try (var client = HttpClient.newHttpClient()) {
                int port = awaitPort(process, log);
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/public/info")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                org.assertj.core.api.Assertions.assertThatThrownBy(trap::accept).isInstanceOf(java.net.SocketTimeoutException.class);
                assertThat(Files.readString(log)).doesNotContain("private-export-canary", "OTEL_EXPORTER", "authorization=");
                assertNoCredentials(Files.readString(log));
            } finally { stop(process); }
        }
    }

    @Test
    void executableJarOffersMetricsOnlyOnExplicitSeparateLoopbackPort() throws Exception {
        int managementPort;
        try (var reservation = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { managementPort = reservation.getLocalPort(); }
        Path log = temporaryDirectory.resolve("local-metrics.log");
        var process = start(log, "--server.port=0", "--management.server.port=" + managementPort,
                "--management.server.address=127.0.0.1", "--fiscuvia.observability.local-metrics=true",
                "--management.endpoint.metrics.access=read-only", "--management.endpoints.web.exposure.include=health,metrics");
        try (var client = HttpClient.newHttpClient()) {
            int port = awaitPort(process, log);
            var main = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/metrics")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(main.statusCode()).isEqualTo(404);
            var metrics = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/metrics")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(metrics.statusCode()).isEqualTo(200);
            assertThat(metrics.body()).contains("jvm.memory.used", "fiscuvia.migrations.ready", "jdbc.connections.active");
            for (String endpoint : List.of("env", "beans", "heapdump", "configprops", "loggers")) {
                assertThat(client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/" + endpoint)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            }
            assertNoCredentials(Files.readString(log));
        } finally { stop(process); }
    }

    private int awaitPort(Process process, Path log) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        var pattern = Pattern.compile("\\\"port\\\":(\\d+)");
        while (process.isAlive() && System.nanoTime() < deadline) {
            String output = Files.readString(log);
            if (output.contains("APPLICATION_READY")) {
                var matcher = pattern.matcher(output);
                if (matcher.find()) return Integer.parseInt(matcher.group(1));
            }
            Thread.sleep(100);
        }
        throw new AssertionError("JAR did not become ready: " + DATABASE.redact(Files.readString(log)));
    }

    private void assertNoCredentials(String output) {
        if (output.contains(DATABASE.runtimePassword) || output.contains(DATABASE.migrationPassword)
                || output.contains(DATABASE.getPassword()) || output.contains(DATABASE.sessionPassword)
                || output.contains(DATABASE.actorKey)) {
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
