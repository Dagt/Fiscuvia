package com.fiscuvia.platform.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ScaffoldRuntimeConfigurationTests {

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void acceptsSupportedActiveProfiles(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        assertThatCode(() -> new ScaffoldRuntimeConfiguration(environment)).doesNotThrowAnyException();
    }

    @Test
    void acceptsLocalDefault() {
        var environment = new MockEnvironment();
        environment.setDefaultProfiles("local");
        assertThatCode(() -> new ScaffoldRuntimeConfiguration(environment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "staging", "unknown"})
    void rejectsUnsupportedProfilesEvenAlongsideLocal(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local", profile);
        assertThatIllegalStateException()
                .isThrownBy(() -> new ScaffoldRuntimeConfiguration(environment))
                .withMessageContaining("supports only local/test");
    }

    @Test
    void rejectsAnUnsupportedDefault() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new ScaffoldRuntimeConfiguration(new MockEnvironment()));
    }
}
