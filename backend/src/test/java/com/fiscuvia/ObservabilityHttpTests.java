package com.fiscuvia;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fiscuvia.platform.internal.observability.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.testcontainers.junit.jupiter.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.sampling.probability=1")
@Import({ObservabilityHttpTests.Fixtures.class, ObservabilityHttpTests.Capture.class, ContractFixtureSecurity.class})
@Testcontainers
@DirtiesContext
class ObservabilityHttpTests {
    static final String CANARY = "DEMO900101XX1-private-token-cookie-CFDI";
    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @LocalServerPort int port;
    @Autowired MeterRegistry meters;
    @Autowired InMemorySpanExporter exporter;
    @Autowired Capture capture;
    final ObjectMapper json = new ObjectMapper();

    @Test void concurrentRequestsHaveIndependentTrustedCorrelationAndSafeActualSpans() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 20; i++) futures.add(client.sendAsync(request("/api/v1/public/info?secret=" + CANARY), HttpResponse.BodyHandlers.ofString()));
            var ids = new HashSet<String>();
            for (var future : futures) {
                var response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);
                String id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
                assertThat(UUID.fromString(id)).isNotNull();
                assertThat(ids.add(id)).isTrue();
            }
            await(() -> completed(ids).size() == 20);
            var logs = completed(ids);
            assertThat(logs).allSatisfy(log -> {
                assertThat(log.path("trace_id").asString()).matches("[0-9a-f]{32}").isNotEqualTo("1".repeat(32));
                assertThat(log.path("span_id").asString()).matches("[0-9a-f]{16}");
                assertThat(exporter.getFinishedSpanItems()).anySatisfy(span -> {
                    assertThat(span.getTraceId()).isEqualTo(log.path("trace_id").asString());
                    assertThat(span.getSpanId()).isEqualTo(log.path("span_id").asString());
                });
            });
            assertPrivateTelemetry();
        }
    }

    @Test void errorsTimeoutsAndUnknownPathsRemainSafeAndBounded() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var failure = client.send(request("/__observe/failure?secret=" + CANARY), HttpResponse.BodyHandlers.ofString());
            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(json.readTree(failure.body()).path("traceId").asString())
                    .isEqualTo(failure.headers().firstValue("X-Correlation-Id").orElseThrow());
            assertThat(failure.body()).doesNotContain(CANARY, "SQLException", "stack");
            var posted = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__observe/body?secret=" + CANARY))
                    .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(CANARY)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(posted.statusCode()).isEqualTo(204);
            var timeout = client.send(request("/__observe/timeout"), HttpResponse.BodyHandlers.ofString());
            assertThat(timeout.statusCode()).isEqualTo(500);
            for (int i = 0; i < 25; i++) assertThat(client.send(request("/private/" + UUID.randomUUID() + "?secret=" + CANARY), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            await(() -> capture.lines.stream().anyMatch(line -> line.contains("HTTP_TIMEOUT")));
            String timeoutId = timeout.headers().firstValue("X-Correlation-Id").orElseThrow();
            await(() -> completed(Set.of(timeoutId)).size() == 1);
            assertThat(completed(Set.of(timeoutId))).hasSize(1);
            String timeoutTrace = completed(Set.of(timeoutId)).getFirst().path("trace_id").asString();
            assertThat(exporter.getFinishedSpanItems()).anySatisfy(span -> {
                assertThat(span.getTraceId()).isEqualTo(timeoutTrace);
                assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("status"))).isEqualTo("500");
                assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
            });
            assertThat(meters.find("http.server.requests").timers()).allSatisfy(timer ->
                    assertThat(timer.getId().getTag("uri")).isIn("UNMAPPED", "/api/v1/public/info"));
            assertPrivateTelemetry();
        }
    }

    @Test void sensitiveActuatorEndpointsAreUnavailableAndHealthHasNoDetails() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String path : List.of("env", "beans", "configprops", "heapdump", "loggers", "metrics", "prometheus"))
                assertThat(client.send(request("/actuator/" + path), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            for (String path : List.of("", "/liveness", "/readiness")) {
                var response = client.send(request("/actuator/health" + path), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json.readTree(response.body()).propertyNames()).allMatch(name -> Set.of("status", "groups").contains(name));
                assertThat(response.body()).doesNotContain("components", "details", "jdbc:", "PostgreSQL");
            }
            assertThat(meters.get("fiscuvia.database.available").gauge().value()).isEqualTo(1);
            assertThat(meters.get("fiscuvia.migrations.ready").gauge().value()).isEqualTo(1);
        }
    }

    void assertPrivateTelemetry() throws Exception {
        assertThat(String.join("\n", capture.lines)).doesNotContain(CANARY, "Authorization", "SELECT private", "exception.stacktrace");
        assertThat(capture.unclean).isEmpty();
        assertThat(exporter.getFinishedSpanItems()).isNotEmpty().allSatisfy(span -> {
            assertThat(span.getName()).startsWith("http ");
            assertThat(span.getAttributes().toString()).doesNotContain(CANARY, "url", "query", "header", "tenant");
            assertThat(span.getEvents().toString()).doesNotContain(CANARY, "SELECT private");
            assertThat(span.getResource().getAttributes().size()).isEqualTo(1);
        });
        assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().toString())
                .doesNotContain(CANARY, "tenant", "correlation", "token", "private/"));
    }
    List<JsonNode> completed(Set<String> ids) {
        return capture.lines.stream().map(json::readTree).filter(node -> node.path("event").asString().equals("HTTP_REQUEST_COMPLETED")
                && ids.contains(node.path("correlation_id").asString())).toList();
    }
    HttpRequest request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("X-Correlation-Id", CANARY).header("Authorization", "Bearer " + CANARY)
            .header("Cookie", "private=" + CANARY).header("traceparent", "00-" + "1".repeat(32) + "-" + "2".repeat(16) + "-01")
            .header("baggage", "private=" + CANARY).GET().build(); }
    static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(ready.getAsBoolean()).isTrue();
    }

    @TestComponent @RestController
    static class Fixtures {
        @GetMapping("/__observe/failure") Object failure() { throw new IllegalStateException(CANARY, new java.sql.SQLException("SELECT private " + CANARY)); }
        @GetMapping("/__observe/timeout") DeferredResult<String> timeout() { return new DeferredResult<>(100L); }
        @PostMapping("/__observe/body") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
        void body(@RequestBody String body) { assertThat(body).isEqualTo(CANARY); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Capture extends AppenderBase<ILoggingEvent> {
        final Queue<String> lines = new ConcurrentLinkedQueue<>();
        final Queue<String> unclean = new ConcurrentLinkedQueue<>();
        final AllowlistLogFormatter formatter = new AllowlistLogFormatter();
        Capture() { start(); ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(this); }
        @Override protected void append(ILoggingEvent event) { lines.add(formatter.format(event)); }
        @Bean InMemorySpanExporter testSpanExporter() { return InMemorySpanExporter.create(); }
        @Bean LocalTracingCustomizer testTracing(InMemorySpanExporter exporter) { return builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter)); }
        @Bean FilterRegistrationBean<Filter> cleanupProbe() {
            var bean = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                try { chain.doFilter(request, response); }
                finally { if (MDC.get("correlationId") != null || SafeLogContext.current() != null) unclean.add("unclean"); }
            });
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE); bean.setAsyncSupported(true); return bean;
        }
        @jakarta.annotation.PreDestroy void detach() { ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(this); stop(); }
    }
}
