# 테스트 규칙

> Spring Boot 통합/슬라이스 테스트와 Testcontainers 운용 규칙. 신규 테스트 작성 시 이 문서를 따른다.

## 목차

- [컨테이너 설정 (Testcontainers)](#컨테이너-설정-testcontainers)
- [통합 테스트 작성](#통합-테스트-작성)
- [JPA 슬라이스 테스트 작성](#jpa-슬라이스-테스트-작성)
- [Flyway 마이그레이션 테스트](#flyway-마이그레이션-테스트)
- [컨텍스트 캐싱 보호](#컨텍스트-캐싱-보호)
- [검증 명령어](#검증-명령어)
- [선택 최적화 (개인 설정)](#선택-최적화-개인-설정)

---

## 컨테이너 설정 (Testcontainers)

- **IMPORTANT**: `jdbc:tc:` JDBC URL 드라이버 방식 **사용 금지**. JVM shutdown hook으로만 컨테이너가 정리되어 Gradle daemon / IDE JVM 재사용 시 누수가 발생한다.
- **ALWAYS** `@ServiceConnection` 기반 `TestcontainersConfiguration` (`src/test/.../config/TestcontainersConfiguration.kt`)을 통해 MySQL 컨테이너를 사용한다. Ryuk reaper가 lifecycle을 보장한다.
- 테스트 프로파일 `src/test/resources/application.yml`에서 `spring.flyway.enabled: false`를 유지한다. schema는 Hibernate `ddl-auto: create-drop`이 담당하고, Flyway 마이그레이션 검증은 `FlywayMigrationTest`만 직접 호출한다.

## 통합 테스트 작성

`@SpringBootTest`가 필요한 테스트는 다음 두 패턴 중 하나만 사용한다.

| 케이스 | 패턴 |
|---|---|
| 추가 어노테이션 (`@AutoConfigureMockMvc` 등) 필요 없음 | `IntegrationTestSupport` 상속 |
| MockMvc 등 어노테이션 조합 필요 | `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration::class)` 직접 부여 |

```kotlin
// 패턴 1: base 상속 (단순 컨텍스트 로딩)
class ServerApplicationTests : IntegrationTestSupport()

// 패턴 2: @Import 직접 부여 (MockMvc 컨트롤러 테스트)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PartyControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    // ...
)
```

## JPA 슬라이스 테스트 작성

`@DataJpaTest`가 필요한 Repository 테스트는 `JpaSliceTestSupport` 상속.

```kotlin
class UserRepositoryTest @Autowired constructor(
    private val userRepository: UserRepository,
) : JpaSliceTestSupport()
```

## Flyway 마이그레이션 테스트

`FlywayMigrationTest`처럼 Flyway를 직접 검증하는 raw JDBC 테스트는 자체 `MySQLContainer`를 띄우되 **반드시 unique label**을 부여한다.

```kotlin
private companion object {
    @JvmStatic
    private val MYSQL: MySQLContainer<*> =
        MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withLabel("purpose", "flyway-migration-test")  // IMPORTANT: reuse 격리
            .withReuse(true)
            .also { it.start() }
}
```

**WHY**: `TestcontainersConfiguration`과 동일한 이미지·옵션이면 reuse hash가 매칭되어 같은 컨테이너를 공유한다. Flyway가 만든 FK (`fk_party_owner` 등) 가 통합 테스트로 누출되면 `ddl-auto: create-drop`이 FK를 모르고 drop하지 못해 데이터 무결성 충돌이 발생한다.

## 컨텍스트 캐싱 보호

Spring TestContext 캐시는 어노테이션 조합 (fingerprint) 이 정확히 일치할 때만 컨텍스트를 재사용한다. 다음 패턴은 컨텍스트를 분리시키므로 **불가피한 경우가 아니면 사용 금지**.

- `@MockBean`, `@SpyBean`, `@MockitoBean` — 빈 교체 시 컨텍스트 분리
- `@TestPropertySource`, `@ActiveProfiles` — 프로파일·프로퍼티 분리
- `@SpringBootTest(properties = [...], classes = [...])` — 옵션 차이 분리

현재 캐싱되는 컨텍스트는 3개다. 새 통합 테스트는 이 fingerprint 중 하나에 정확히 맞춰야 한다.

| Fingerprint | 적용 테스트 |
|---|---|
| `@SpringBootTest + @Import(TC)` | `IntegrationTestSupport` 상속 |
| `@SpringBootTest + @AutoConfigureMockMvc + @Import(TC)` | MockMvc 컨트롤러 테스트들 |
| `@DataJpaTest + @Import(TC)` | `JpaSliceTestSupport` 상속 |

## 검증 명령어

```bash
# 전체 테스트
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "com.team2.server.party.controller.PartyControllerTest"

# 단일 테스트 메서드 (한글 백틱 이름)
./gradlew test --tests "com.team2.server.party.controller.PartyControllerTest.인증 없이*"

# 컨테이너 누수 확인 — 테스트 종료 후 잔존 컨테이너 0개여야 정상
docker ps -a --filter "label=org.testcontainers"
```

## 선택 최적화 (개인 설정)

본인 머신에서만 적용되는 옵션. 팀 공통 설정 아님.

```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

활성화 시 2차 실행부터 컨테이너 재사용으로 ~25% 단축 (1m 24s → 1m 3s, 213 tests 기준).
