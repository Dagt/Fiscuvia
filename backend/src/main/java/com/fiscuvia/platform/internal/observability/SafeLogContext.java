package com.fiscuvia.platform.internal.observability;

import java.util.UUID;
import org.slf4j.MDC;

/** Trusted typed context, separate from arbitrary MDC strings and logging arguments. */
public final class SafeLogContext implements AutoCloseable {
    public record Context(UUID correlationId, String traceId, String spanId) {
        public Context {
            if (correlationId == null) throw new IllegalArgumentException("Correlation is required");
            if (traceId == null || !traceId.matches("[a-f0-9]{32}") || traceId.equals("0".repeat(32))
                    || spanId == null || !spanId.matches("[a-f0-9]{16}") || spanId.equals("0".repeat(16))) {
                traceId = null;
                spanId = null;
            }
        }
    }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private final Context previous;
    private final java.util.Map<String, String> previousMdc;

    public SafeLogContext(Context context) {
        previous = CURRENT.get();
        previousMdc = MDC.getCopyOfContextMap();
        CURRENT.set(context);
        MDC.put("correlationId", context.correlationId().toString());
        if (context.traceId() != null) { MDC.put("traceId", context.traceId()); MDC.put("spanId", context.spanId()); }
        else { MDC.remove("traceId"); MDC.remove("spanId"); }
    }
    public static Context current() { return CURRENT.get(); }
    @Override public void close() {
        try { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        finally { MDC.clear(); if (previousMdc != null) MDC.setContextMap(previousMdc); }
    }
}
