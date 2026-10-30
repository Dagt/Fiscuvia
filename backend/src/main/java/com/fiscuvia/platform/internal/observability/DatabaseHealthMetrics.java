package com.fiscuvia.platform.internal.observability;

import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration(proxyBeanMethods = false)
class DatabaseHealthMetrics {
    private final AtomicInteger available = new AtomicInteger(-1);
    private final AtomicInteger migrated = new AtomicInteger();
    DatabaseHealthMetrics(MeterRegistry registry) {
        Gauge.builder("fiscuvia.database.available", available, AtomicInteger::get).register(registry);
        Gauge.builder("fiscuvia.migrations.ready", migrated, AtomicInteger::get).register(registry);
    }
    @EventListener(ApplicationReadyEvent.class)
    void startupMigrationsCompleted() { migrated.set(1); }

    @Bean(name = "dbHealthIndicator")
    HealthIndicator restrictedDatabaseHealth(DataSource source) {
        return () -> {
            try (var connection = source.getConnection(); var query = connection.createStatement()) {
                query.setQueryTimeout(2);
                try (var result = query.executeQuery("SELECT 1")) {
                    boolean up = result.next() && result.getInt(1) == 1;
                    available.set(up ? 1 : 0);
                    return up ? Health.up().build() : Health.down().build();
                }
            } catch (Exception unavailable) {
                available.set(0);
                return Health.down().build(); // No throwable/details or driver message in health or logs.
            }
        };
    }
}
