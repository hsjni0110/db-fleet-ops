package com.dbfleetops.operation;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class OperationArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.dbfleetops");

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses().that().resideInAPackage("com.dbfleetops.operation.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.dbfleetops.operation.adapter..")
                .check(classes);
    }

    @Test
    void inboundAdaptersDoNotDependOnServiceImplementations() {
        noClasses().that().resideInAnyPackage("com.dbfleetops.operation.adapter.webapi..",
                        "com.dbfleetops.operation.adapter.integration..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.dbfleetops.operation.application")
                .check(classes);
    }

    @Test
    void portsDoNotDependOnFrameworkTypes() {
        noClasses().that().resideInAnyPackage("..application.required..",
                        "..application.provided..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..",
                        "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void operationCoreDoesNotUseOtherDomainModels() {
        noClasses().that().resideInAnyPackage("com.dbfleetops.operation.application..",
                        "com.dbfleetops.operation.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dbfleetops.agent.domain..", "com.dbfleetops.agent.dto..",
                        "com.dbfleetops.agent.infra..", "com.dbfleetops.database.domain..",
                        "com.dbfleetops.database.dto..", "com.dbfleetops.database.infra..",
                        "com.dbfleetops.backup.domain..", "com.dbfleetops.backup.dto..",
                        "com.dbfleetops.backup.infra..", "com.dbfleetops.policy.domain..",
                        "com.dbfleetops.policy.dto..", "com.dbfleetops.policy.infra..")
                .check(classes);
    }
}
