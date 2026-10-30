package com.fiscuvia.platform.internal.http;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import com.fiscuvia.platform.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;

final class SafeProblems {
    record FieldViolation(String field, String code, String message) {}

    private SafeProblems() {}

    static ProblemDetail create(ApiErrorCode code, HttpServletRequest request) {
        String traceId = CorrelationFilter.id(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(code.status()), code.detail());
        problem.setTitle(code.title());
        problem.setType(URI.create("urn:fiscuvia:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        // URLs can contain identifiers/secrets. An opaque occurrence URI avoids reflecting paths and queries.
        problem.setInstance(URI.create("urn:uuid:" + traceId));
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", traceId);
        problem.setProperty("fieldErrors", List.of());
        return problem;
    }

    static FieldViolation field(String field, String constraint) {
        String safeField = field != null && field.matches("[a-zA-Z][a-zA-Z0-9]{0,63}") ? field : "request";
        String code = switch (constraint == null ? "" : constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Size", "Length" -> "SIZE";
            case "Email", "Pattern" -> "FORMAT";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "ExpectedVersion" -> "RANGE";
            default -> "INVALID";
        };
        String message = switch (code) {
            case "REQUIRED" -> "Este campo es obligatorio.";
            case "SIZE" -> "La longitud no está permitida.";
            case "FORMAT" -> "El formato no es válido.";
            case "RANGE" -> "El valor está fuera del rango permitido.";
            default -> "El campo no es válido.";
        };
        return new FieldViolation(safeField, code, message);
    }
}
