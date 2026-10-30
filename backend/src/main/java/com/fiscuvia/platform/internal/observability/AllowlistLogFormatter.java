package com.fiscuvia.platform.internal.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.json.JsonMapper;

/** Projection before serialization. Never serialize messages, arguments, exceptions, MDC or arbitrary key/value pairs. */
public final class AllowlistLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Override public String format(ILoggingEvent event) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("application", "fiscuvia-backend");
        fields.put("severity", event.getLevel().toString());
        String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
        fields.put("component", logger.startsWith("com.fiscuvia.") ? "APPLICATION" : logger.startsWith("org.flywaydb.")
                || logger.startsWith("com.zaxxer.hikari.") || logger.startsWith("org.postgresql.") ? "DATABASE" : "FRAMEWORK");
        fields.put("module", logger.startsWith("com.fiscuvia.") ? "platform" : "runtime");
        fields.put("event", "RUNTIME_LOG");
        var context = SafeLogContext.current();
        if (context != null) {
            fields.put("correlation_id", context.correlationId().toString());
            if (context.traceId() != null) { fields.put("trace_id", context.traceId()); fields.put("span_id", context.spanId()); }
        }
        if (event.getKeyValuePairs() != null) event.getKeyValuePairs().stream()
                .filter(pair -> "operationalEvent".equals(pair.key) && pair.value instanceof OperationalEvent)
                .findFirst().ifPresent(pair -> {
                    var safe = (OperationalEvent) pair.value;
                    fields.put("event", safe.code().name());
                    if (safe.method() != null) fields.put("method", safe.method());
                    if (safe.route() != null) fields.put("route", safe.route());
                    if (safe.status() != null) { fields.put("status", safe.status()); fields.put("outcome", SafeRoutes.outcome(safe.status())); }
                    if (safe.durationMs() != null) fields.put("duration_ms", safe.durationMs());
                    if (safe.port() != null) fields.put("port", safe.port());
                    if (safe.failure() != null) fields.put("failure_code", safe.failure().name());
                    if (safe.fingerprint() != null) fields.put("error_fingerprint", safe.fingerprint());
                });
        return JSON.writeValueAsString(fields) + "\n";
    }
}
