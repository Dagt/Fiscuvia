package com.fiscuvia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FiscuviaApplicationTests {

    @Container
    static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.register(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Test
    void startsWithIsolatedPostgresAndNoOtherExternalServices() {
        assertThat(port).isPositive();
        assertThat(environment.getActiveProfiles()).containsExactly("test");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(DATABASE.runtimeUrl());
        assertThat(environment.getProperty("spring.flyway.user")).isEqualTo(PostgresTestDatabase.MIGRATION_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void servesHealthWithoutComponentDetails(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        var health = objectMapper.readTree(response.body());
        assertThat(health.path("status").asString()).isEqualTo("UP");
        assertThat(health.has("components")).isFalse();
        assertThat(health.has("details")).isFalse();
        assertThat(health.properties()).allSatisfy(field ->
                assertThat(field.getKey()).isIn("status", "groups"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/configprops",
            "/actuator/beans", "/actuator/mappings", "/actuator/metrics",
            "/actuator/heapdump", "/actuator/loggers", "/actuator/shutdown",
            "/actuator/info", "/api/v1/me", "/missing", "/__contract-test/dto"})
    void doesNotExposeSensitiveEndpointsOrUnimplementedBusinessApi(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).isEqualTo(path.equals("/api/v1/me") ? 401 : 404);
        var error = objectMapper.readTree(response.body());
        assertThat(error.has("trace")).isFalse();
        assertThat(error.has("exception")).isFalse();
        assertThat(error.has("message")).isFalse();
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
