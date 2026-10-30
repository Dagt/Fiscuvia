package com.fiscuvia.platform.internal.observability;

import java.util.UUID;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HttpTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger(HttpTelemetry.class);
    private final Tracer tracer;
    public HttpTelemetry(Tracer tracer) { this.tracer = tracer; }
    public SafeLogContext.Context context(UUID id) {
        var span = tracer.currentSpan();
        return new SafeLogContext.Context(id, span == null ? null : span.context().traceId(), span == null ? null : span.context().spanId());
    }
    public void complete(String method, String template, int status, double durationMs) {
        LOG.atInfo().addKeyValue("operationalEvent", new OperationalEvent(OperationalEvent.Code.HTTP_REQUEST_COMPLETED,
                method, template, status, durationMs, null, null, null)).log("HTTP_REQUEST_COMPLETED");
    }
    public void failure(Throwable error) {
        LOG.atError().addKeyValue("operationalEvent", new OperationalEvent(OperationalEvent.Code.API_FAILURE,
                null, null, 500, null, null, SafeDiagnostics.classify(error), SafeDiagnostics.fingerprint(error))).log("API_FAILURE");
    }
    public void timeout() {
        LOG.atWarn().addKeyValue("operationalEvent", new OperationalEvent(OperationalEvent.Code.HTTP_TIMEOUT,
                null, null, 500, null, null, SafeDiagnostics.Failure.TIMEOUT, null)).log("HTTP_TIMEOUT");
    }
}
