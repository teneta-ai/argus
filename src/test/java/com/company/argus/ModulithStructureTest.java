package com.company.argus;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithStructureTest {

    ApplicationModules modules = ApplicationModules.of(AgentApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();
    }

    @Test
    void generateDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
