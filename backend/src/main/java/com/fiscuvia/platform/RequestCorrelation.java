package com.fiscuvia.platform;

import java.util.UUID;
import com.fiscuvia.platform.internal.observability.SafeLogContext;

/** Server-owned correlation only; no header/MDC parsing and no authorization semantics. */
public final class RequestCorrelation {
    private RequestCorrelation() {}
    public static UUID currentOrCreate() {
        var context=SafeLogContext.current();
        return context==null ? UUID.randomUUID() : context.correlationId();
    }
}
