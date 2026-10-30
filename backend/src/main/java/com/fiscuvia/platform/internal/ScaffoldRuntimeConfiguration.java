package com.fiscuvia.platform.internal;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Prevents this unauthenticated scaffold from masquerading as a remote environment. */
@Configuration(proxyBeanMethods = false)
class ScaffoldRuntimeConfiguration {

    private static final Set<String> SUPPORTED_PROFILES = Set.of("local", "test");

    @Bean
    static BeanFactoryPostProcessor validateBeforeDatabaseConnections(Environment environment) {
        return beanFactory -> {
            validateProfiles(environment);
            String runtimeUrl = environment.getProperty("spring.datasource.url");
            validateDatabaseUrl(runtimeUrl);
            String migrationUrl = environment.getProperty("spring.flyway.url", runtimeUrl);
            validateDatabaseUrl(migrationUrl);
            if (!runtimeUrl.equals(migrationUrl)) {
                throw new IllegalStateException("Runtime and migration URLs must target the same local database.");
            }
        };
    }

    static void validateDatabaseUrl(String url) {
        try {
            if (url == null || !url.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }
            URI parsed = URI.create(url.substring("jdbc:".length()));
            if (!Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(parsed.getHost())
                    || !"/fiscuvia_local".equals(parsed.getPath()) || parsed.getUserInfo() != null
                    || parsed.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            if (parsed.getQuery() != null) {
                for (String parameter : parsed.getQuery().split("&")) {
                    if (!parameter.matches("currentSchema=app|connectTimeout=[1-9][0-9]*|loggerLevel=OFF")) {
                        throw new IllegalArgumentException();
                    }
                }
            }
        } catch (RuntimeException invalidUrl) {
            // Do not attach input/URI exceptions: they could contain credentials.
            throw new IllegalStateException("Database URL must target loopback/fiscuvia_local without credentials or unsupported options.");
        }
    }

    ScaffoldRuntimeConfiguration(Environment environment) {
        validateProfiles(environment);
    }

    private static void validateProfiles(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] effectiveProfiles = activeProfiles.length == 0
                ? environment.getDefaultProfiles() : activeProfiles;

        if (effectiveProfiles.length == 0
                || !Arrays.stream(effectiveProfiles).allMatch(SUPPORTED_PROFILES::contains)) {
            throw new IllegalStateException(
                    "Issue #20 scaffold supports only local/test profiles. "
                            + "Remote environments require the security and delivery issues.");
        }
    }
}
