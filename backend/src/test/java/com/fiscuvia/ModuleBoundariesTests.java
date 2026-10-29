package com.fiscuvia;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleBoundariesTests {

    @Test
    void discoversAndVerifiesActualProductionModules() {
        var modules = ApplicationModules.of(FiscuviaApplication.class);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactly("platform");
        modules.verify();
    }

    @Test
    void rejectsAccessToAnotherModulesInternals() {
        var modules = ApplicationModules.of("architecture.fixtures.illegal", location -> true);
        assertThatThrownBy(modules::verify)
                .hasMessageContaining("non-exposed type")
                .hasMessageContaining("HiddenService");
    }

    @Test
    void rejectsCyclicModuleDependencies() {
        var modules = ApplicationModules.of("architecture.fixtures.cyclic", location -> true);
        assertThatThrownBy(modules::verify).hasMessageContaining("Cycle detected");
    }
}
