package com.team2.server.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * B. 명명 규칙 / 패키지 위치.
 *
 * 활성화 Phase: 2 (party 4-레이어 재배치 후)
 */
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class PackageStructureTest {
    /** B1: *Controller 는 ..api.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val controllerClassesOnlyInApiPackage: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .resideInAPackage("..api..")
            .`as`("*Controller 클래스는 ..api.. 에만 위치")

    /** B2: *UseCase 는 ..application.usecase.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val useCaseClassesOnlyInApplicationUseCasePackage: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("UseCase")
            .should()
            .resideInAPackage("..application.usecase..")
            .`as`("*UseCase 클래스는 ..application.usecase.. 에만 위치")

    /**
     * B3: *Service 는 ..application.service.. 에만 (Spring 확장 클래스 예외).
     *
     * 예외:
     *  - DefaultOAuth2UserService 상속 클래스(`CustomOAuth2UserService`) — Spring 확장
     */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val serviceClassesOnlyInApplicationServicePackage: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .areNotAssignableTo("org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService")
            .should()
            .resideInAPackage("..application.service..")
            .`as`("*Service 클래스는 ..application.service.. 에만 위치 (Spring 확장 예외)")

    /** B4: *Repository 는 ..infrastructure.persistence.. 에만. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val repositoryClassesOnlyInInfrastructurePersistencePackage: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .should()
            .resideInAPackage("..infrastructure.persistence..")
            .`as`("*Repository 클래스는 ..infrastructure.persistence.. 에만 위치")

    /** B5: ..application.usecase.. 안의 클래스는 *UseCase 접미사. */
    @ArchTest
    @ArchIgnore(reason = "Phase 2: 마이그레이션 후 활성화")
    val classesInUseCasePackageShouldEndWithUseCase: ArchRule =
        classes()
            .that()
            .resideInAPackage("..application.usecase..")
            .should()
            .haveSimpleNameEndingWith("UseCase")
            .`as`("..application.usecase.. 안의 클래스는 *UseCase 접미사 필수")
}
