package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * E. Cross-feature 규칙.
 *
 * 활성화 Phase: 3 (user application 레이어 신설 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class CrossFeatureRuleTest {
    private val features = ArchUnitConstants.FEATURES

    /** E1: 다른 feature 의 infrastructure 패키지 직접 접근 금지. */
    @ArchTest
    @ArchIgnore(reason = "Phase 3: user application 신설 후 활성화")
    val crossFeatureShouldNotAccessOtherInfrastructure: ArchRule =
        compose(
            "다른 feature 의 infrastructure 직접 접근 금지",
            features.flatMap { from ->
                features.filter { it != from }.map { to ->
                    noClasses()
                        .that()
                        .resideInAPackage("..$from..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..$to.infrastructure..")
                        .`as`("$from feature 는 $to.infrastructure 직접 접근 금지")
                }
            },
        )

    /** E2: feature A 의 Service 는 feature B 의 어떤 패키지도 의존 금지. */
    @ArchTest
    @ArchIgnore(reason = "Phase 3: user application 신설 후 활성화")
    val serviceShouldNotDependOnOtherFeatures: ArchRule =
        compose(
            "Service 는 다른 feature 의존 금지 (UseCase 가 조합)",
            features.flatMap { from ->
                features.filter { it != from }.map { to ->
                    noClasses()
                        .that()
                        .resideInAPackage("..$from.application.service..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..$to..")
                        .`as`("$from.application.service 는 $to feature 의존 금지")
                }
            },
        )

    /** E3: feature A 의 Domain 은 다른 feature 의존 금지. */
    @ArchTest
    @ArchIgnore(reason = "Phase 3: user application 신설 후 활성화")
    val domainShouldNotDependOnOtherFeatures: ArchRule =
        compose(
            "Domain 은 다른 feature 의존 금지",
            features.flatMap { from ->
                features.filter { it != from }.map { to ->
                    noClasses()
                        .that()
                        .resideInAPackage("..$from.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..$to..")
                        .`as`("$from.domain 은 $to feature 의존 금지")
                }
            },
        )

    private fun compose(
        description: String,
        rules: List<ArchRule>,
    ): ArchRule = CompositeArchRule.of(rules).`as`(description)
}
