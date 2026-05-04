package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * H. 순환 참조 금지.
 *
 * 활성화 Phase: 0 (즉시)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class PackageCycleTest {
    /** H1: feature 패키지 간 순환 참조 금지. */
    @ArchTest
    val noPackageCyclesBetweenFeatures: ArchRule =
        slices()
            .matching("com.team2.server.(*)..")
            .should()
            .beFreeOfCycles()
            .`as`("패키지 간 순환 참조 금지")
}
