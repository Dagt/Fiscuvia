package com.fiscuvia.platform.internal.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MeterPrivacyTests {
    @Test void arbitraryTagsAndMeterNamesCannotCreateSensitiveTimeSeries() {
        var registry = new SimpleMeterRegistry();
        try {
            registry.config().meterFilter(new PrivateMeterPolicy());
            for (int i = 0; i < 50; i++) registry.counter("http.server.requests", "uri", "/private/" + i,
                    "tenant", "private-" + i, "correlationId", "private-" + i, "method", "private-" + i).increment();
            registry.counter("private-canary", "secret", "private-canary").increment();
            assertThat(registry.getMeters()).hasSize(1);
            var counter = registry.get("http.server.requests").counter();
            assertThat(counter.count()).isEqualTo(50);
            assertThat(counter.getId().getTags()).extracting(Object::toString).containsExactly("tag(method=OTHER)", "tag(uri=UNMAPPED)");
        } finally { registry.close(); }
    }
    @Test void exporterImplementationIsAbsentFromRuntimeClasspath() {
        assertThat(org.springframework.util.ClassUtils.isPresent("io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter", getClass().getClassLoader())).isFalse();
        assertThat(org.springframework.util.ClassUtils.isPresent("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter", getClass().getClassLoader())).isFalse();
    }
}
