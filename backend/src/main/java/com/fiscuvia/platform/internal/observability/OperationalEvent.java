package com.fiscuvia.platform.internal.observability;

/** The logging boundary accepts this typed closed event, never an arbitrary field map. */
public record OperationalEvent(Code code, String method, String route, Integer status, Double durationMs,
        Integer port, SafeDiagnostics.Failure failure, String fingerprint) {
    public enum Code { HTTP_REQUEST_COMPLETED, HTTP_TIMEOUT, API_FAILURE, APPLICATION_READY, APPLICATION_FAILED }
    public OperationalEvent {
        if (code == null) throw new IllegalArgumentException("Event required");
        if (method != null) method = SafeRoutes.method(method);
        if (route != null) route = SafeRoutes.route(route);
        if (status != null && (status < 100 || status > 599)) throw new IllegalArgumentException("Invalid status");
        if (durationMs != null && (!Double.isFinite(durationMs) || durationMs < 0)) throw new IllegalArgumentException("Invalid duration");
        if (port != null && (port < 0 || port > 65535)) throw new IllegalArgumentException("Invalid port");
        if (fingerprint != null && !fingerprint.matches("[a-f0-9]{16}")) throw new IllegalArgumentException("Invalid fingerprint");
    }
}
