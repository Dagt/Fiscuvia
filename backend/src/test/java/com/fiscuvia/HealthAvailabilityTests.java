package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.hikari.connection-timeout=500", "spring.datasource.hikari.validation-timeout=250"})
@Testcontainers @DirtiesContext
class HealthAvailabilityTests {
    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @LocalServerPort int port;
    @Autowired MeterRegistry meters;
    @Test void databaseOutageChangesReadinessButNotLivenessWithoutLeakingDetails() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertThat(get(client, "readiness").statusCode()).isEqualTo(200);
            DATABASE.stop();
            var down = get(client, "readiness");
            assertThat(down.statusCode()).isEqualTo(503);
            assertThat(down.body()).isEqualTo("{\"status\":\"DOWN\"}");
            assertThat(get(client, "liveness").statusCode()).isEqualTo(200);
            assertThat(meters.get("fiscuvia.database.available").gauge().value()).isZero();
        }
    }
    HttpResponse<String> get(HttpClient client, String probe) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/" + probe))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
