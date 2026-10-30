package com.fiscuvia.platform.internal.observability;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.assertj.core.api.Assertions.*;

class TracingConfigurationTests {
    @Test void samplingZeroRecordsNoSpansAndSamplingOneRecordsRealSpans() {
        for (double probability : new double[]{0, 1}) {
            var exporter = InMemorySpanExporter.create();
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("capture", (LocalTracingCustomizer) builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter)));
            try (var provider = new ObservabilityConfiguration().localTraceProvider(probability, beans.getBeanProvider(LocalTracingCustomizer.class))) {
                provider.get("test").spanBuilder("approved operation").startSpan().end();
                assertThat(exporter.getFinishedSpanItems()).hasSize((int) probability);
            }
        }
    }
    @Test void invalidSamplingFailsExplicitly() {
        var beans = new DefaultListableBeanFactory();
        for (double value : new double[]{-1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThatThrownBy(() -> new ObservabilityConfiguration().localTraceProvider(value, beans.getBeanProvider(LocalTracingCustomizer.class)))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}
