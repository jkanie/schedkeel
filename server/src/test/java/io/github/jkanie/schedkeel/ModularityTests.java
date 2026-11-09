package io.github.jkanie.schedkeel;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(SchedkeelApplication.class);

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        modules.verify();
    }

    @Test
    void writesModuleDiagrams() {
        new Documenter(modules).writeDocumentation();
    }
}
