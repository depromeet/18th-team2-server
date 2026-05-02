package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController

/**
 * C. 어노테이션 위치.
 *
 * 활성화 Phase: 2 (4-레이어 재배치 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class AnnotationRuleTest {
    /** C1: @RestController / @Controller 는 ..api.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val restControllerAnnotationsOnlyInApiPackage: ArchRule =
        classes()
            .that()
            .areAnnotatedWith(RestController::class.java)
            .or()
            .areAnnotatedWith(Controller::class.java)
            .should()
            .resideInAPackage("..api..")
            .`as`("@RestController/@Controller 는 ..api.. 에만 선언")

    /** C2-class: 클래스 레벨 @Transactional 은 ..application.usecase.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val classLevelTransactionalOnlyOnUseCase: ArchRule =
        classes()
            .that()
            .areAnnotatedWith(Transactional::class.java)
            .should()
            .resideInAPackage("..application.usecase..")
            .`as`("클래스 레벨 @Transactional 은 UseCase 에만 선언")

    /** C2-method: 메서드 레벨 @Transactional 도 ..application.usecase.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val methodLevelTransactionalOnlyOnUseCase: ArchRule =
        methods()
            .that()
            .areAnnotatedWith(Transactional::class.java)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..application.usecase..")
            .`as`("메서드 레벨 @Transactional 은 UseCase 에만 선언")
}
