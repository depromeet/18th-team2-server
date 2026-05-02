package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

/**
 * A. 레이어 의존 방향 검증.
 *
 * 활성화 Phase:
 *  - A4, A5: Phase 0 (즉시)
 *  - A1~A3: Phase 2 (party 4-레이어 재배치 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class LayerDependencyTest {
    /** A1~A4: api → usecase → service → infrastructure 단방향 + domain은 모두에서 참조. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: party 4-레이어 재배치 후 활성화")
    val layeredArchitectureRules: ArchRule =
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Api")
            .definedBy("..api..")
            .layer("UseCase")
            .definedBy("..application.usecase..")
            .layer("Service")
            .definedBy("..application.service..")
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Infrastructure")
            .definedBy("..infrastructure..")
            .whereLayer("Api")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("UseCase")
            .mayOnlyBeAccessedByLayers("Api")
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("UseCase")
            .whereLayer("Infrastructure")
            .mayOnlyBeAccessedByLayers("Service", "UseCase")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Api", "UseCase", "Service", "Infrastructure")
            .`as`("레이어 의존 방향: api → usecase → service → infrastructure, domain은 모두에서 참조")

    /** A4: Domain은 application/api/infrastructure 의존 금지. */
    @ArchTest
    val domainShouldNotDependOnOuterLayers: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..api..",
                "..application..",
                "..infrastructure..",
            ).`as`("Domain은 application/api/infrastructure 의존 금지")

    /** A5: Domain은 Spring Data JPA 의존 금지. */
    @ArchTest
    val domainShouldNotDependOnSpringData: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.data..")
            .`as`("Domain은 Spring Data JPA 의존 금지")
}
