# ArchUnit Architecture Tests Design

- 작성일: 2026-05-02
- 대상: `com.team2.server` (Kotlin + Spring Boot)
- 의존 문서: [`2026-04-29-layered-architecture-design.md`](./2026-04-29-layered-architecture-design.md)
- 목적: 레이어드 아키텍처 규칙을 컴파일/테스트 단계에서 자동 검증

---

## 1. 결정 사항

- **검증 도구**: ArchUnit 1.4.0 (JUnit 5 통합)
- **적용 강도**: Strict-Now — 모든 규칙을 즉시 추가, 마이그레이션 단계에 따라 `@ArchIgnore`로 단계별 활성화
- **테스트 분할**: 레이어별로 6개 파일 분할
- **커버리지**: A(레이어 의존) + B(명명/패키지) + C(어노테이션) + D(금지 호출) + E(Cross-feature) + H(순환 참조) + X(common)
- **위치**: `src/test/kotlin/com/team2/server/architecture/`

---

## 2. 의존성 추가

`build.gradle.kts`:
```kotlin
dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}
```

---

## 3. 테스트 파일 구성

```
src/test/kotlin/com/team2/server/architecture/
├── ArchUnitConstants.kt          공통 상수 (BASE_PACKAGE, FEATURES 등)
├── LayerDependencyTest.kt        A. 레이어 의존 방향
├── PackageStructureTest.kt       B. 명명 규칙 / 패키지 위치
├── AnnotationRuleTest.kt         C. 어노테이션 위치
├── ForbiddenCallRuleTest.kt      D. 금지 호출
├── CrossFeatureRuleTest.kt       E. feature 경계
├── PackageCycleTest.kt           H. 순환 참조
└── CommonPackageRuleTest.kt      X. common 패키지
```

각 파일은 다음 골격을 따른다:
```kotlin
@AnalyzeClasses(
    packages = ["com.team2.server"],
    importOptions = [ImportOption.DoNotIncludeTests::class, ImportOption.DoNotIncludeJars::class],
)
class XxxTest {
    @ArchTest
    val rule_name: ArchRule = ...
}
```

---

## 4. 공통 상수 (`ArchUnitConstants.kt`)

```kotlin
object ArchUnitConstants {
    const val BASE_PACKAGE = "com.team2.server"

    val FEATURES = listOf("auth", "user", "party", "image", "chat", "rollingpaper")

    const val API = "..api.."
    const val USECASE = "..application.usecase.."
    const val SERVICE = "..application.service.."
    const val APPLICATION = "..application.."
    const val DOMAIN = "..domain.."
    const val INFRASTRUCTURE = "..infrastructure.."
    const val COMMON = "..common.."
}
```

---

## 5. 검증 규칙 카탈로그 (총 22개)

### A. 레이어 의존 방향 (5)

| # | 규칙 | 활성화 Phase |
|---|---|---|
| A1 | Controller(api)는 UseCase만 의존 | 2 |
| A2 | UseCase는 Service, Domain만 의존 (다른 feature UseCase 추가 허용) | 2 |
| A3 | Service는 Repository, Domain만 의존 | 2 |
| A4 | Domain은 application/api/infrastructure 의존 ❌ | 0 |
| A5 | Domain은 `JpaRepository` / `org.springframework.data.*` 의존 ❌ | 0 |

A1~A4는 `Architectures.layeredArchitecture()` DSL로 구현:
```kotlin
@ArchTest
val layered_architecture_rules: ArchRule =
    layeredArchitecture()
        .consideringAllDependencies()
        .layer("Api").definedBy("..api..")
        .layer("UseCase").definedBy("..application.usecase..")
        .layer("Service").definedBy("..application.service..")
        .layer("Domain").definedBy("..domain..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .whereLayer("Api").mayNotBeAccessedByAnyLayer()
        .whereLayer("UseCase").mayOnlyBeAccessedByLayers("Api")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("UseCase")
        .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Service", "UseCase")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "UseCase", "Service", "Infrastructure")
        .as("레이어 의존 방향: api → usecase → service → infrastructure, domain은 모두에서 참조")
```

A5:
```kotlin
@ArchTest
val domain_should_not_depend_on_spring_data: ArchRule =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
        .as("Domain은 Spring Data JPA 의존 금지")
```

### B. 명명 규칙 / 패키지 위치 (5)

| # | 규칙 | Phase |
|---|---|---|
| B1 | `*Controller` 는 `..api..` 에만 | 2 |
| B2 | `*UseCase` 는 `..application.usecase..` 에만 | 2 |
| B3 | `*Service` 는 `..application.service..` 에만 (Spring 확장 클래스 예외 허용) | 2 |
| B4 | `*Repository` 는 `..infrastructure.persistence..` 에만 | 2 |
| B5 | `..application.usecase..` 안의 클래스는 `*UseCase` 접미사 | 2 |

B3 예외 처리 (auth feature의 `CustomOAuth2UserService` 같은 Spring 확장):
```kotlin
@ArchTest
val service_classes_only_in_application_service: ArchRule =
    classes()
        .that().haveSimpleNameEndingWith("Service")
        .and().areNotAssignableTo("org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService")
        .should().resideInAPackage("..application.service..")
```

### C. 어노테이션 위치 (3)

| # | 규칙 | Phase |
|---|---|---|
| C1 | `@RestController` / `@Controller` 는 `..api..` 에만 | 2 |
| C2 | `@Transactional` 은 `..application.usecase..` 에만 | 2 |
| C3 | `@Component` / `@Service` 는 `..application..` 또는 `..infrastructure..` 또는 `..domain.policy..` 에만 | 2 |

```kotlin
@ArchTest
val transactional_only_on_usecase: ArchRule =
    classes()
        .that().areAnnotatedWith(Transactional::class.java)
        .or().containAnyMethodsThat(areAnnotatedWith(Transactional::class.java))
        .should().resideInAPackage("..application.usecase..")
        .as("@Transactional은 UseCase에만 선언")
```

### D. 금지 호출 (3)

| # | 규칙 | Phase |
|---|---|---|
| D1 | UseCase는 `JpaRepository.save/saveAndFlush/delete/deleteAll/deleteById` 호출 ❌ | 2 |
| D2 | Service는 다른 Service를 필드로 의존 ❌ | 2 |
| D3 | UseCase는 같은 feature 내 다른 UseCase 의존 ❌ (cross-feature는 허용) | 2 |

D1:
```kotlin
@ArchTest
val usecase_should_not_call_repository_writes: ArchRule =
    noClasses()
        .that().resideInAPackage("..application.usecase..")
        .should().callMethodWhere(
            JavaCall.Predicates.target(
                HasName.Predicates.nameMatching("save|saveAndFlush|delete|deleteAll|deleteById")
                    .and(HasOwner.Predicates.With.owner(assignableTo(JpaRepository::class.java)))
            )
        )
        .as("UseCase는 Repository 쓰기 호출 금지 (Service 위임)")
```

D2:
```kotlin
@ArchTest
val service_should_not_have_other_service_field: ArchRule =
    noClasses()
        .that().resideInAPackage("..application.service..")
        .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("..application.service..")
            .and().areNotAssignableFrom(JavaClass.Predicates.simpleNameOf(thisClassName))
        .as("Service는 다른 Service 의존 금지 (조합은 UseCase가)")
```

D3 — 같은 feature 판별을 위한 커스텀 조건:
```kotlin
@ArchTest
val usecase_should_not_depend_on_same_feature_usecase: ArchRule =
    FEATURES.map { feature ->
        noClasses()
            .that().resideInAPackage("..$feature.application.usecase..")
            .should().dependOnClassesThat()
                .resideInAPackage("..$feature.application.usecase..")
                .and().areNotAssignableFrom(thisClass)
    }.reduce(ArchRule::and)
```

### E. Cross-feature 규칙 (3)

| # | 규칙 | Phase |
|---|---|---|
| E1 | feature A → feature B의 `infrastructure` 의존 ❌ | 3 |
| E2 | feature A의 Service → feature B의 어떤 패키지 의존 ❌ | 3 |
| E3 | feature A의 Domain → feature B 의존 ❌ | 3 |

```kotlin
@ArchTest
val cross_feature_infrastructure_access: ArchRule =
    FEATURES.flatMap { from ->
        FEATURES.filter { it != from && it != "common" }.map { to ->
            noClasses()
                .that().resideInAPackage("..$from..")
                .should().dependOnClassesThat().resideInAPackage("..$to.infrastructure..")
        }
    }.reduce(ArchRule::and)
    .as("다른 feature의 infrastructure 직접 접근 금지")
```

### H. 순환 참조 (1)

| # | 규칙 | Phase |
|---|---|---|
| H1 | `com.team2.server.{feature}` 패키지 간 순환 참조 ❌ | 0 |

```kotlin
@ArchTest
val no_package_cycles: ArchRule =
    slices()
        .matching("com.team2.server.(*)..")
        .should().beFreeOfCycles()
        .as("패키지 간 순환 참조 금지")
```

### X. common 패키지 (2)

| # | 규칙 | Phase |
|---|---|---|
| X1 | `common.*` 은 어떤 feature 패키지도 의존 ❌ | 0 |
| X2 | `..domain..` 클래스는 `common.exception`, `common.persistence`만 사용 | 1 |

```kotlin
@ArchTest
val common_should_not_depend_on_features: ArchRule =
    noClasses()
        .that().resideInAPackage("..common..")
        .should().dependOnClassesThat().resideInAnyPackage(
            *FEATURES.map { "..$it.." }.toTypedArray()
        )
        .as("common 패키지는 feature 패키지 의존 금지")
```

---

## 6. 단계별 활성화 전략

`@ArchIgnore` 어노테이션으로 마이그레이션 진행에 따라 점진 활성화.

| Phase | 마이그레이션 시점 | 활성화 규칙 |
|---|---|---|
| 0 | 즉시 (현재 코드 통과 가능) | A4, A5, H1, X1 |
| 1 | common 정리 PR 후 | X2 |
| 2 | party 4-레이어 재배치 PR 후 | A1, A2, A3, B1~B5, C1, C2, C3, D1, D2, D3 |
| 3 | user application 신설 PR 후 | E1, E2, E3 (user-party 간) |
| 4 | auth 4-레이어 재배치 PR 후 | (이미 활성화된 모든 규칙이 전 패키지에 적용) |

`@ArchIgnore` 해제는 각 마이그레이션 PR에서 같이 진행.

---

## 7. CI 통합

- 기존 `./gradlew test` 에 자동 포함 — 별도 Gradle task 불필요
- 로컬 단독 실행: `./gradlew test --tests "com.team2.server.architecture.*"`
- 실패 메시지는 한국어로 `as("...")` 사용

---

## 8. 한 페이지 요약

```
ArchUnit 1.4.0 (JUnit5)

검증 영역 (총 22개 규칙)
─────────────────────────
A. 레이어 의존 방향        5
B. 명명 / 패키지 위치       5
C. 어노테이션 위치          3
D. 금지 호출                3
E. Cross-feature           3
H. 순환 참조                1
X. common 패키지           2

활성화 전략
─────────────────────────
Phase 0 (즉시)   : A4, A5, H1, X1
Phase 1 (common): + X2
Phase 2 (party) : + A1~A3, B1~B5, C1~C3, D1~D3
Phase 3 (user)  : + E1~E3
Phase 4 (auth)  : 전체 활성

테스트 위치
─────────────────────────
src/test/kotlin/com/team2/server/architecture/ (6 파일)
```
