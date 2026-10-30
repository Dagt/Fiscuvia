package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import com.fiscuvia.platform.TenantTransactions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test") @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TenantHttpPrivacyTests.Probe.class) @ExtendWith(OutputCaptureExtension.class)
@Testcontainers @DirtiesContext
class TenantHttpPrivacyTests {
    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @LocalServerPort int port;
    @Autowired MeterRegistry meters;
    final ObjectMapper json = new ObjectMapper();
    static final String CANARY = "DEMO900101XX1-private-tenant-token";
    @Test void headersQueryAndBodyNeverEstablishTenant(CapturedOutput output) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__tenancy-test/unscoped?tenant_id=" + CANARY))
                    .header("X-Tenant-Id", "00000000-0000-4000-8000-000000000001")
                    .header("Authorization", "Bearer " + CANARY).POST(HttpRequest.BodyPublishers.ofString(CANARY)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).isEqualTo("0");
            assertThat(output.getAll()).doesNotContain(CANARY, "X-Tenant-Id");
            assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().toString()).doesNotContain(CANARY, "tenant_id", "00000000-0000"));
            for (String path : List.of("/api/v1/tenants", "/api/v1/tenant-context", "/actuator/tenants"))
                assertThat(client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
    }
    @Test void internalContextFailureKeepsProblemDetailsSafeAndLogsOnlyApprovedCode(CapturedOutput output) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__tenancy-test/rejected?private=" + CANARY)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
            assertThat(json.readTree(response.body()).path("traceId").asString()).isEqualTo(response.headers().firstValue("X-Correlation-Id").orElseThrow());
            assertThat(response.body()).doesNotContain(CANARY, "SQL", "TenantContextRejected", "tenant_id", "stack");
            assertThat(output.getAll()).contains("TENANT_CONTEXT_REJECTED").doesNotContain(CANARY, "SELECT count", "Tenant transaction context rejected");
        }
    }
    @TestComponent @RestController
    static class Probe {
        private final JdbcTemplate sql;
        private final TenantTransactions transactions;
        Probe(JdbcTemplate sql, TenantTransactions transactions) { this.sql=sql; this.transactions=transactions; }
        @PostMapping("/__tenancy-test/unscoped") int unscoped() { return sql.queryForObject("SELECT count(*) FROM app.tenants", Integer.class); }
        @GetMapping("/__tenancy-test/rejected") Object rejected() { return transactions.execute(null, () -> null); }
    }
}
