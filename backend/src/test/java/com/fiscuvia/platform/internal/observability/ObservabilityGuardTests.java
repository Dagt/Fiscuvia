package com.fiscuvia.platform.internal.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ObservabilityGuardTests {
    private MockEnvironment local() {
        var env = new MockEnvironment(); env.setActiveProfiles("local");
        return env.withProperty("management.endpoints.web.exposure.include", "health,metrics")
                .withProperty("management.endpoint.metrics.access", "read-only")
                .withProperty("fiscuvia.observability.local-metrics", "true")
                .withProperty("management.server.address", "127.0.0.1").withProperty("management.server.port", "18081");
    }
    @Test void permitsDefaultHealthAndExplicitSeparateLoopbackDiagnostics() {
        ObservabilityGuard.validate(new MockEnvironment());
        ObservabilityGuard.validate(local());
    }
    @Test void rejectsGlobalExposurePublicBindingSamePortAndTestProfileDiagnostics() {
        for (var env : new MockEnvironment[] {
                local().withProperty("management.endpoints.web.exposure.include", "*"),
                local().withProperty("management.server.address", "0.0.0.0"),
                local().withProperty("management.server.port", "8080"),
                local().withProperty("management.server.port", "65536"),
                local().withProperty("management.endpoint.health.show-details", "always"),
                local().withProperty("management.endpoint.health.show-components", "always"),
                local().withProperty("fiscuvia.observability.local-metrics", "false"),
                local().withProperty("management.endpoints.access.default", "read-only")}) {
            assertThatThrownBy(() -> ObservabilityGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        }
        var env = local(); env.setActiveProfiles("test");
        assertThatThrownBy(() -> ObservabilityGuard.validate(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsUnimplementedExportConfiguration() {
        assertThatThrownBy(() -> ObservabilityGuard.validate(new MockEnvironment()
                .withProperty("management.opentelemetry.tracing.export.otlp.enabled", "true"))).isInstanceOf(IllegalStateException.class);
    }
}
