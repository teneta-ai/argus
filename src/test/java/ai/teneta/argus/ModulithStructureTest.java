package ai.teneta.argus;

import org.junit.jupiter.api.Disabled;
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
    @Disabled("Run manually — writes to disk and slows CI")
    void generateDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
