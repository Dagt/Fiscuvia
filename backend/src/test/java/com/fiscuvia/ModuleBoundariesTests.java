package com.fiscuvia;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleBoundariesTests {

    @Test void domainModulesMustNotDependOnTheLowerLevelRlsPort() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that()
            .resideOutsideOfPackages("com.fiscuvia.platform..","com.fiscuvia.identity.internal..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.fiscuvia.platform.TenantTransactions")
            .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.fiscuvia"));
    }

    @Test
    void discoversAndVerifiesActualProductionModules() {
        var modules = ApplicationModules.of(FiscuviaApplication.class);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("platform", "identity", "audit");
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
