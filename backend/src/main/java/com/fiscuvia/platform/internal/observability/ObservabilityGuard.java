package com.fiscuvia.platform.internal.observability;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ObservabilityGuard {
    @Bean static BeanFactoryPostProcessor restrictDiagnostics(Environment environment) {
        return factory -> validate(environment);
    }
    static void validate(Environment env) {
        Set<String> exposure = Arrays.stream(env.getProperty("management.endpoints.web.exposure.include", "health").split(","))
                .map(String::trim).collect(Collectors.toSet());
        boolean metrics = exposure.contains("metrics") || !"none".equals(env.getProperty("management.endpoint.metrics.access", "none"));
        if (!Set.of("health", "metrics").containsAll(exposure)
                || !"none".equals(env.getProperty("management.endpoints.access.default", "none"))
                || !"never".equals(env.getProperty("management.endpoint.health.show-details", "never"))
                || !"never".equals(env.getProperty("management.endpoint.health.show-components", "never"))) reject();
        if (metrics) {
            Set<String> profiles = Set.of(env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles());
            if (!profiles.equals(Set.of("local")) || !env.getProperty("fiscuvia.observability.local-metrics", Boolean.class, false)
                    || !"127.0.0.1".equals(env.getProperty("management.server.address"))
                    || !"127.0.0.1".equals(env.getProperty("server.address", "127.0.0.1"))
                    || !env.containsProperty("management.server.port")
                    || env.getProperty("management.server.port", Integer.class, -1) < 0
                    || env.getProperty("management.server.port", Integer.class, -1) > 65535
                    || env.getProperty("management.server.port").equals(env.getProperty("server.port", "8080"))
                    || !"read-only".equals(env.getProperty("management.endpoint.metrics.access"))) reject();
        }
        if (env.getProperty("management.opentelemetry.tracing.export.otlp.enabled", Boolean.class, false)
                || env.getProperty("management.otlp.metrics.export.enabled", Boolean.class, false)) reject();
    }
    private static void reject() { throw new IllegalStateException("Observability diagnostics configuration rejected"); }
}
