package com.dbfleetops.operation;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class OperationArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.dbfleetops");

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses().that().resideInAPackage("com.dbfleetops.operation..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dbfleetops.operation..adapter..")
                .check(classes);
    }

    @Test
    void inboundAdaptersDoNotDependOnServiceImplementations() {
        noClasses().that().resideInAnyPackage("com.dbfleetops.operation..adapter.webapi..",
                        "com.dbfleetops.operation..adapter.scheduler..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.dbfleetops.operation..application.service..",
                        "com.dbfleetops.operation..application.execution..")
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
        noClasses().that().resideInAnyPackage("com.dbfleetops.operation..application..",
                        "com.dbfleetops.operation..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.dbfleetops.agent.domain..", "com.dbfleetops.agent.dto..",
                        "com.dbfleetops.agent.infra..", "com.dbfleetops.database.domain..",
                        "com.dbfleetops.database.dto..", "com.dbfleetops.database.infra..",
                        "com.dbfleetops.backup.domain..", "com.dbfleetops.backup.dto..",
                        "com.dbfleetops.backup.infra..", "com.dbfleetops.policy.domain..",
                        "com.dbfleetops.policy.dto..", "com.dbfleetops.policy.infra..")
                .check(classes);
    }

    @Test
    void policyApplicationDoesNotDependOnOperation() {
        noClasses().that().resideInAPackage("com.dbfleetops.policy.application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.dbfleetops.operation..application..",
                        "com.dbfleetops.operation..domain..", "com.dbfleetops.operation..dto..")
                .check(classes);
    }

    @Test
    void jobAndTaskCoreDoNotDependOnWorkflow() {
        noClasses().that().resideInAnyPackage("com.dbfleetops.operation.job..",
                        "com.dbfleetops.operation.task.application..",
                        "com.dbfleetops.operation.task.domain..",
                        "com.dbfleetops.operation.task.dto..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.dbfleetops.operation.workflow..")
                .check(classes);
    }

    @Test
    void taskAdaptersOnlyUseWorkflowProvidedPorts() {
        noClasses().that().resideInAPackage("com.dbfleetops.operation.task.adapter..")
                .should().dependOnClassesThat(resideInAnyPackage(
                                "com.dbfleetops.operation.workflow..")
                        .and(not(resideInAnyPackage(
                                "com.dbfleetops.operation.workflow.application.provided.."))))
                .check(classes);
    }

    @Test
    void jobAndTaskDomainsDoNotDependOnEachOther() {
        noClasses().that().resideInAPackage("com.dbfleetops.operation.job.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.dbfleetops.operation.task.domain..")
                .check(classes);
        noClasses().that().resideInAPackage("com.dbfleetops.operation.task.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.dbfleetops.operation.job.domain..")
                .check(classes);
    }

    @Test
    void workerSchedulerDoesNotReportJobResults() {
        noClasses().that().haveSimpleName("OperationJobClaimScheduler")
                .should().dependOnClassesThat().haveSimpleName("JobReports")
                .check(classes);
    }
}
