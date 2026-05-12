package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * X. common 패키지 규칙.
 *
 * 활성화 Phase:
 *  - X1: Phase 0 (즉시)
 *  - X2: PR 2 (common 정리 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class CommonPackageRuleTest {
    private val featurePackages = ArchUnitConstants.FEATURES.map { "..$it.." }.toTypedArray()

    /** X1: common 패키지는 어떤 feature 패키지도 의존 금지. */
    @ArchTest
    val commonShouldNotDependOnFeatures: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*featurePackages)
            .`as`("common 패키지는 feature 패키지 의존 금지")

    /** X2: domain 은 common.web/config 의존 금지 (exception/persistence/image 만 허용). */
    @ArchTest
    val domainShouldNotDependOnWebLayerCommon: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..common.web..",
                "..common.config..",
            ).`as`("Domain 은 common 의 web/config 의존 금지")
}
