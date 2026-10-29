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

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the repackaged JAR, without the test classpath or external infrastructure. */
@Timeout(45)
class FiscuviaApplicationIT {

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
            throw new AssertionError("Executable JAR did not become healthy:\n" + Files.readString(log));
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

    private void assertStartupFailure(String name, String expectedDiagnostic, String... arguments)
            throws Exception {
        Path log = temporaryDirectory.resolve(name + ".log");
        Process process = start(log, arguments);
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("JAR should fail promptly").isTrue();
            assertThat(process.exitValue()).isNotZero();
            assertThat(Files.readString(log)).contains(expectedDiagnostic);
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
                || key.startsWith("SERVER_") || key.startsWith("MANAGEMENT_"));
        return builder.start();
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
