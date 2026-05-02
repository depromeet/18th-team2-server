package com.team2.server.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.data.repository.Repository

/**
 * D. 금지 호출.
 *
 * 활성화 Phase: 2 (4-레이어 재배치 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class ForbiddenCallRuleTest {
    /**
     * D1: UseCase 는 Repository 쓰기 메서드 호출 금지.
     * (save / saveAndFlush / saveAll / delete / deleteAll / deleteById / deleteAllById)
     */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val useCaseShouldNotCallRepositoryWriteMethods: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..application.usecase..")
            .should(callWriteMethodOnSpringRepository())
            .`as`("UseCase는 Repository 쓰기 호출 금지 (Service에 위임)")

    /** D2: Service 는 다른 Service 의존 금지 (자기 자신 제외). */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val serviceShouldNotDependOnOtherService: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..application.service..")
            .should(dependOnAnotherServiceClass())
            .`as`("Service는 다른 Service 의존 금지 (조합은 UseCase가)")

    /**
     * D3: UseCase 는 같은 feature 내 다른 UseCase 의존 금지.
     * 다른 feature 의 UseCase 는 허용.
     */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val useCaseShouldNotDependOnSameFeatureUseCase: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..application.usecase..")
            .should(dependOnSameFeatureUseCase())
            .`as`("UseCase는 같은 feature 내 다른 UseCase 의존 금지")

    private fun callWriteMethodOnSpringRepository(): ArchCondition<JavaClass> {
        val writeMethods =
            setOf(
                "save",
                "saveAndFlush",
                "saveAll",
                "delete",
                "deleteAll",
                "deleteById",
                "deleteAllById",
            )
        return object : ArchCondition<JavaClass>("call Repository write methods") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.methodCallsFromSelf.forEach { call ->
                    val targetOwner = call.targetOwner
                    val isRepository =
                        targetOwner.isAssignableTo(Repository::class.java) ||
                            targetOwner.simpleName.endsWith("Repository")
                    if (isRepository && call.target.name in writeMethods) {
                        events.add(
                            SimpleConditionEvent.violated(
                                call,
                                "${item.fullName} 가 ${targetOwner.simpleName}.${call.target.name} 호출",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun dependOnAnotherServiceClass(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("depend on another *Service class in application.service") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { it != item }
                    .filter { it.simpleName.endsWith("Service") }
                    .filter { it.packageName.contains(".application.service") }
                    .forEach { target ->
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "${item.simpleName} 가 다른 Service ${target.simpleName} 의존",
                            ),
                        )
                    }
            }
        }

    private fun dependOnSameFeatureUseCase(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("depend on UseCase in same feature") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val itemFeature = extractFeature(item.packageName) ?: return
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { it != item }
                    .filter { it.simpleName.endsWith("UseCase") }
                    .filter { it.packageName.contains(".application.usecase") }
                    .filter { extractFeature(it.packageName) == itemFeature }
                    .forEach { target ->
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "${item.simpleName} 가 같은 feature($itemFeature) UseCase ${target.simpleName} 의존",
                            ),
                        )
                    }
            }
        }

    private fun extractFeature(packageName: String): String? {
        val prefix = "com.team2.server."
        if (!packageName.startsWith(prefix)) return null
        val rest = packageName.removePrefix(prefix)
        return rest.substringBefore('.').takeIf { it.isNotEmpty() }
    }
}
