package com.fiscuvia.platform.internal.observability;

import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

/** Explicit code extension point for an independently authorized collector; none installed in production. */
@FunctionalInterface
public interface LocalTracingCustomizer {
    void customize(SdkTracerProviderBuilder builder);
}
