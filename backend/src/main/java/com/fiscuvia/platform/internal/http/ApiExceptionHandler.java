package com.fiscuvia.platform.internal.http;

import java.util.Comparator;
import com.fiscuvia.platform.ApiErrorCode;
import com.fiscuvia.platform.ApiFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final com.fiscuvia.platform.internal.observability.HttpTelemetry telemetry;
    ApiExceptionHandler(com.fiscuvia.platform.internal.observability.HttpTelemetry telemetry) { this.telemetry = telemetry; }

    @InitBinder
    void strictUuidBinding(WebDataBinder binder) {
        // Spring's legacy UUID property editor can precede ConversionService for MVC arguments.
        binder.registerCustomEditor(java.util.UUID.class, new java.beans.PropertyEditorSupport() {
            @Override public void setAsText(String text) {
                setValue(com.fiscuvia.platform.ApiIdentifiers.uuid(text));
            }
        });
    }

    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<Object> explicit(ApiFailure exception, HttpServletRequest request) {
        return response(SafeProblems.create(exception.code(), request), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        // Operational diagnostics project approved codes only; never pass raw throwable text to logs.
        telemetry.failure(exception);
        org.springframework.web.filter.ServerHttpObservationFilter.findObservationContext(request)
                .ifPresent(context -> context.setError(exception));
        return response(SafeProblems.create(ApiErrorCode.INTERNAL_ERROR, request), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The superclass warning includes the caller-supplied method. Do not log exception text.
        return handleExceptionInternal(exception, null, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The superclass warning includes the request URI, which may carry private identifiers.
        return handleExceptionInternal(exception, null, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = SafeProblems.create(ApiErrorCode.VALIDATION_FAILED, servlet(request));
        problem.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> SafeProblems.field(error.getField(), error.getCode()))
                .distinct().sorted(Comparator.comparing(SafeProblems.FieldViolation::field))
                .limit(20).toList());
        return response(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(SafeProblems.create(exception.isForReturnValue()
                ? ApiErrorCode.INTERNAL_ERROR : ApiErrorCode.VALIDATION_FAILED, servlet(request)), headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(SafeProblems.create(ApiErrorCode.forStatus(status.value()), servlet(request)), headers);
    }

    private static HttpServletRequest servlet(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private static ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        // Preserve semantic protocol headers only; arbitrary exception headers can contain private data.
        if (!original.getAllow().isEmpty()) headers.setAllow(original.getAllow());
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        if (problem.getStatus() == 401) headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return new ResponseEntity<>(problem, headers, HttpStatusCode.valueOf(problem.getStatus()));
    }
}
