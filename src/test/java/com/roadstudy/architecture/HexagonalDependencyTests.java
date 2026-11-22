package com.roadstudy.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("헥사고날 계층의 의존성 방향")
class HexagonalDependencyTests {
    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.roadstudy");

        for (String layer : new String[]{"adapter", "application", "domain"}) {
            String packagePrefix = "com.roadstudy." + layer + ".";
            assertThat(productionClasses.stream()
                    .anyMatch(javaClass -> javaClass.getPackageName().startsWith(packagePrefix)))
                    .as("%s 계층의 운영 클래스가 검사 대상에 포함되어야 함", layer)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("""
            허용: Adapter → Application → Domain
            허용: Adapter → Domain
            금지: Domain → Application·Adapter
            금지: Application → Adapter
            """)
    void dependenciesFollowHexagonalLayers() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Adapter").definedBy("com.roadstudy.adapter..")
                .layer("Application").definedBy("com.roadstudy.application..")
                .layer("Domain").definedBy("com.roadstudy.domain..")
                .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
                .check(productionClasses);
    }

    @Test
    @DisplayName("금지: Domain → 설정·Spring·JSON·외부 SDK")
    void domainDoesNotDependOnInfrastructure() {
        // 기존 JPA 엔티티 방식 유지로 jakarta.persistence 의존은 허용
        noClasses()
                .that().resideInAPackage("com.roadstudy.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.roadstudy.config..",
                        "org.springframework..", "com.fasterxml..",
                        "com.google..", "org.apache.pdfbox..")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage("com.roadstudy.domain..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.roadstudy.global.exception.GlobalExceptionHandler")
                .check(productionClasses);
    }

    @Test
    @DisplayName("금지: Application → 설정·HTTP·JPA·외부 SDK")
    void applicationDoesNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("com.roadstudy.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.roadstudy.config..",
                        "org.springframework.web..", "org.springframework.http..",
                        "org.springframework.core.io..", "org.springframework.data..",
                        "jakarta.persistence..", "com.fasterxml..",
                        "com.google..", "org.apache.pdfbox..")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage("com.roadstudy.application..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.roadstudy.global.exception.GlobalExceptionHandler")
                .check(productionClasses);
    }

    @Test
    @DisplayName("금지: Web → 서비스 구현체·Outbound Port·다른 기술 어댑터")
    void webUsesInboundPorts() {
        noClasses()
                .that().resideInAPackage("com.roadstudy.adapter.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.roadstudy.application.study.service..",
                        "com.roadstudy.application.study.port.out..",
                        "com.roadstudy.adapter.persistence..",
                        "com.roadstudy.adapter.storage..",
                        "com.roadstudy.adapter.pdf..",
                        "com.roadstudy.adapter.openai..",
                        "com.roadstudy.adapter.tts..")
                .check(productionClasses);
    }
}
