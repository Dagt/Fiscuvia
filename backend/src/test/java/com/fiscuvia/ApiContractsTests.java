package com.fiscuvia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fiscuvia.platform.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiContractsTests.FixtureController.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class ApiContractsTests {
    private static final String CANARY = "synthetic-private-value";
    private static final String VALID = "{\"displayName\":\"DEMO\",\"referenceId\":\"00000000-0000-4000-8000-000000000001\",\"category\":\"DEMO\",\"expectedVersion\":0}";
    private static final List<Evidence> EVIDENCE = new ArrayList<>();
    record Evidence(String scenario, int status, String contentType, String correlationId, JsonNode body) {}

    @Container static final PostgresTestDatabase DATABASE = new PostgresTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { DATABASE.register(registry); }
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    @AfterAll static void writeContractEvidence() throws Exception {
        Files.writeString(Path.of("target/contract-responses.json"), new ObjectMapper().writeValueAsString(EVIDENCE));
    }

    @Test void publicInfoIsTypedMinimalAndDoesNotReflectCallerHeaders() throws Exception {
        var response = call("GET", "/api/v1/public/info?token=" + CANARY, null, "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).propertyNames()).containsExactlyInAnyOrder("productName", "tagline", "apiVersion");
        assertThat(mapper.readTree(response.body()).path("productName").asString()).isEqualTo("Fiscuvia");
        assertThat(mapper.readTree(response.body()).path("apiVersion").asString()).isEqualTo("1.0.0");
        evidence("public-info", response);
        assertSafe(response);
    }

    @ParameterizedTest @EnumSource(ApiErrorCode.class)
    void allStableErrorCodesHaveSafeHttpProblems(ApiErrorCode code) throws Exception {
        var response = call("GET", "/__contract-test/errors/" + code.name(), null, "application/json");
        assertProblem(response, code.status(), code.name());
        if (code.status() == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"displayName\":\"\"}", "{\"displayName\":\"DEMO\",\"category\":\"invalid-private-format\"}"})
    void requiredAndFormatValidationHasSafeFieldErrors(String json) throws Exception {
        var response = call("POST", "/__contract-test/dto", json, "application/json");
        assertProblem(response, 422, "VALIDATION_FAILED");
        assertThat(mapper.readTree(response.body()).path("fieldErrors").size()).isPositive();
        assertThat(response.body()).doesNotContain("invalid-private-format", "template-private-message");
    }

    @Test void validDtoIsAcceptedAndRangeLengthFailuresAreServerSide() throws Exception {
        assertThat(call("POST", "/__contract-test/dto", VALID, "application/json").statusCode()).isEqualTo(200);
        for (String json : List.of(VALID.replace("DEMO\",\"reference", "X".repeat(41) + "\",\"reference"),
                VALID.replace("\"expectedVersion\":0", "\"expectedVersion\":-1"),
                VALID.replace("\"expectedVersion\":0", "\"expectedVersion\":9007199254740992"))) {
            assertProblem(call("POST", "/__contract-test/dto", json, "application/json"), 422, "VALIDATION_FAILED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "{\"unknown\":\"synthetic-private-value\"}",
            "{\"displayName\":123}", "{\"expectedVersion\":\"0\"}", "{\"expectedVersion\":1.5}",
            "{\"referenceId\":\"1-1-1-1-1\"}", "{\"referenceId\":\"not-a-uuid\"}"})
    void malformedJsonAndInvalidTypesAre400WithoutRejectedValues(String json) throws Exception {
        assertProblem(call("POST", "/__contract-test/dto", json, "application/json"), 400, "BAD_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=10001", "size=0", "size=101", "sort=password,asc", "sort=id,sideways", "sort=id,asc,desc"})
    void paginationAndSortAreBounded(String query) throws Exception {
        assertProblem(call("GET", "/__contract-test/page?" + query, null, "application/json"), 400, "INVALID_PAGINATION");
    }

    @Test void transportTypesCanonicalUuidsAndMethodValidationAreChecked() throws Exception {
        assertProblem(call("GET", "/__contract-test/page?page=bad", null, "application/json"), 400, "BAD_REQUEST");
        for (String id : List.of("not-a-uuid", "1-1-1-1-1")) {
            assertProblem(call("GET", "/__contract-test/uuid/" + id, null, "application/json"), 400, "BAD_REQUEST");
        }
        assertProblem(call("GET", "/__contract-test/method?count=0", null, "application/json"), 422, "VALIDATION_FAILED");
        var page = call("GET", "/__contract-test/page", null, "application/json");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(page.body()).path("size").asInt()).isEqualTo(20);
    }

    @Test void unexpectedFailuresAndExplicitSpringErrorsNeverExposePrivateDetails(CapturedOutput output) throws Exception {
        assertProblem(call("GET", "/__contract-test/explode?token=" + CANARY, null, "application/json"), 500, "INTERNAL_ERROR");
        assertProblem(call("GET", "/__contract-test/spring-error", null, "application/json"), 409, "CONFLICT");
        assertProblem(call("GET", "/__contract-test/framework-error", null, "application/json"), 405, "METHOD_NOT_ALLOWED");
        assertProblem(call("GET", "/error?message=" + CANARY, null, "text/html"), 500, "INTERNAL_ERROR");
        assertThat(output.getAll()).doesNotContain(CANARY, "SELECT private_data", "template-private-message");
    }

    @Test void unknownRoutesMethodsAndNegotiationUseSafeProblems() throws Exception {
        assertProblem(call("GET", "/api/v1/me?token=" + CANARY, null, "application/json"), 404, "NOT_FOUND");
        assertProblem(call("GET", "/missing/" + CANARY, null, "application/json"), 404, "NOT_FOUND");
        var wrongMethod = call("POST", "/api/v1/public/info", "{}", "application/json");
        assertProblem(wrongMethod, 405, "METHOD_NOT_ALLOWED");
        assertThat(wrongMethod.headers().firstValue("Allow").orElse("")).contains("GET");
        assertProblem(call("GET", "/api/v1/public/info", null, "text/html"), 406, "NOT_ACCEPTABLE");
        assertProblem(call("POST", "/__contract-test/dto", VALID, "text/plain"), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    private HttpResponse<String> call(String method, String path, String body, String media) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).header("X-Correlation-Id", CANARY).header("Accept", media);
            if (body != null) builder.header("Content-Type", media).method(method, HttpRequest.BodyPublishers.ofString(body));
            else builder.method(method, HttpRequest.BodyPublishers.noBody());
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        JsonNode problem = mapper.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(status);
        assertThat(problem.path("code").asString()).isEqualTo(code);
        String trace = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(problem.path("traceId").asString()).isEqualTo(trace);
        assertThat(problem.path("instance").asString()).isEqualTo("urn:uuid:" + trace);
        assertSafe(response);
        evidence("error-" + code, response);
    }

    private void assertSafe(HttpResponse<String> response) {
        String trace = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(UUID.fromString(trace).toString()).isEqualTo(trace);
        assertThat(response.body()).doesNotContain(CANARY, "SELECT private_data", "jdbc:", "java.lang", "stackTrace", "exception", "rejectedValue", "template-private-message");
    }

    private void evidence(String scenario, HttpResponse<String> response) throws Exception {
        EVIDENCE.add(new Evidence(scenario, response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                response.headers().firstValue("X-Correlation-Id").orElse(""), mapper.readTree(response.body())));
    }

    @RestController @TestComponent
    static class FixtureController {
        record FixtureRequest(@NotBlank @Size(max = 40) String displayName, @NotNull UUID referenceId,
                @Pattern(regexp = "[A-Z]{2,8}", message = "template-private-message") String category,
                @ExpectedVersion Long expectedVersion) {}

        @GetMapping("/__contract-test/errors/{code}") Object error(@PathVariable ApiErrorCode code) { throw new ApiFailure(code); }
        @PostMapping("/__contract-test/dto") Object dto(@Valid @RequestBody FixtureRequest body) { return Map.of("accepted", true); }
        @GetMapping("/__contract-test/page") Object page(@RequestParam(required = false) Integer page,
                @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
            var query = PageQuery.parse(page, size, sort, Set.of("id", "createdAt"));
            return new PageResponse<>(List.of(), query.page(), query.size(), false);
        }
        @GetMapping("/__contract-test/uuid/{id}") Object uuid(@PathVariable UUID id) { return Map.of("id", id); }
        @GetMapping("/__contract-test/method") Object method(@RequestParam @Min(1) int count) { return Map.of("count", count); }
        @GetMapping("/__contract-test/explode") Object explode() { throw new IllegalStateException(CANARY + " SELECT private_data"); }
        @GetMapping("/__contract-test/spring-error") Object springError() { throw new ResponseStatusException(HttpStatus.CONFLICT, CANARY); }
        @GetMapping("/__contract-test/framework-error") Object frameworkError() throws org.springframework.web.HttpRequestMethodNotSupportedException {
            throw new org.springframework.web.HttpRequestMethodNotSupportedException(CANARY, List.of("GET"));
        }
    }
}
