package com.fiscuvia.platform.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ScaffoldRuntimeConfigurationTests {

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://127.0.0.1:55433/fiscuvia_local?currentSchema=app&connectTimeout=3",
            "jdbc:postgresql://localhost:5432/fiscuvia_local?loggerLevel=OFF"})
    void acceptsOnlyTheLocalDatabase(String url) {
        assertThatCode(() -> ScaffoldRuntimeConfiguration.validateDatabaseUrl(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:test", "jdbc:postgresql://remote.example.test/fiscuvia_local",
            "jdbc:postgresql://localhost/production", "jdbc:postgresql://name:synthetic@localhost/fiscuvia_local",
            "jdbc:postgresql://localhost/fiscuvia_local?password=synthetic", "malformed"})
    void rejectsUnsafeDatabaseTargetsWithoutEchoingValues(String url) {
        assertThatIllegalStateException().isThrownBy(() -> ScaffoldRuntimeConfiguration.validateDatabaseUrl(url))
                .withMessage("Database URL must target loopback/fiscuvia_local without credentials or unsupported options.");
    }

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
