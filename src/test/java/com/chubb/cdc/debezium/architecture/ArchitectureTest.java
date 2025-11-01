package com.chubb.cdc.debezium.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests to enforce Clean Architecture principles and layer dependencies.
 * Per Constitution Principle VI: Domain layer must have no framework dependencies.
 */
@DisplayName("Architecture Tests")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.chubb.cdc.debezium");
    }

    @Test
    @DisplayName("Domain layer should not depend on application, infrastructure, or presentation layers")
    void domainLayerShouldNotDependOnOtherLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure..",
                        "..presentation.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain layer should not depend on Spring Framework")
    void domainLayerShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.boot.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain layer should not depend on Debezium")
    void domainLayerShouldNotDependOnDebezium() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("io.debezium..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain layer should not depend on Kafka")
    void domainLayerShouldNotDependOnKafka() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.apache.kafka..",
                        "org.springframework.kafka.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Application layer should not depend on infrastructure or presentation layers")
    void applicationLayerShouldNotDependOnInfrastructureOrPresentation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..presentation.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Infrastructure layer should not depend on presentation layer")
    void infrastructureLayerShouldNotDependOnPresentation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("..presentation..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Layered architecture should be respected")
    void layeredArchitectureShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Infrastructure").definedBy("..infrastructure..")
                .layer("Presentation").definedBy("..presentation..")

                .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Presentation")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Presentation")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation");

        rule.check(classes);
    }

    @Test
    @DisplayName("Repository implementations should reside in infrastructure layer")
    void repositoryImplementationsShouldBeInInfrastructure() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("RepositoryAdapter")
                .or().haveSimpleNameEndingWith("RepositoryImpl")
                .should().resideInAPackage("..infrastructure..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Repository ports should reside in domain layer")
    void repositoryPortsShouldBeInDomain() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .should().resideInAPackage("..domain..repository..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Use cases should reside in application layer")
    void useCasesShouldBeInApplication() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..application.usecase..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Controllers should reside in presentation layer")
    void controllersShouldBeInPresentation() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..presentation..");

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain model classes should not use Spring annotations")
    void domainModelShouldNotUseSpringAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..model..")
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository");

        rule.check(classes);
    }

    @Test
    @DisplayName("Records in domain should be immutable value objects")
    void domainRecordsShouldBeInModelPackage() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .and().areRecords()
                .should().resideInAnyPackage("..domain..model..", "..domain..event..");

        rule.check(classes);
    }
}
