package com.fiscuvia.platform.internal;

import java.util.Arrays;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Prevents this unauthenticated scaffold from masquerading as a remote environment. */
@Configuration(proxyBeanMethods = false)
class ScaffoldRuntimeConfiguration {

    private static final Set<String> SUPPORTED_PROFILES = Set.of("local", "test");

    ScaffoldRuntimeConfiguration(Environment environment) {
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
