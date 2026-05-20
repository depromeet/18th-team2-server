# 마이페이지 계정 관리 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마이페이지 화면에 필요한 사용자 계정 정보 조회 API (`GET /api/me/account`) 한 개를 layered 구조로 구현한다.

**Architecture:** 신규 `me` feature 패키지(`api` + `application.usecase`)를 만들고, UseCase가 `UserRepository.findById`로 read-only 조회 후 yml 설정의 `support.chat-url`과 함께 DTO로 매핑해 응답한다. 로그아웃은 클라이언트 처리이므로 서버 작업 없음.

**Tech Stack:** Kotlin + Spring Boot, JPA, Spring Security + JWT, JUnit5 + MockMvc + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-05-14-my-page-account-design.md`

**Branch:** `design/my-page-account` (워크트리: `.worktrees/feature-my-page-account`). 최종 구현은 `team-flow`로 만들 별도 feature 브랜치에서 진행.

---

## File Structure

신규 파일:

- `src/main/kotlin/com/team2/server/me/config/SupportProperties.kt` — `support.chat-url` ConfigurationProperties
- `src/main/kotlin/com/team2/server/me/api/dto/MeAccountResponse.kt` — 응답 DTO
- `src/main/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCase.kt` — 조회 흐름 + DTO 매핑
- `src/main/kotlin/com/team2/server/me/api/MeAccountController.kt` — `GET /api/me/account`
- `src/test/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCaseTest.kt` — UseCase 단위 테스트 (fake repo)
- `src/test/kotlin/com/team2/server/me/api/MeAccountControllerTest.kt` — 통합 테스트 (`@SpringBootTest`)

수정 파일:

- `src/main/resources/application.yml` — `support.chat-url` 키 추가
- `src/test/resources/application.yml` — `support.chat-url` 키 추가
- `src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt:6` — `FEATURES` 리스트에 `"me"` 추가

---

## Task 1: ArchUnit FEATURES 리스트에 "me" 추가

**Files:**
- Modify: `src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt:6`

- [ ] **Step 1: FEATURES 리스트에 "me" 추가**

`src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt`의 6번째 줄을 다음과 같이 수정한다:

수정 전:
```kotlin
val FEATURES = listOf("auth", "user", "party", "image", "chat", "rollingpaper")
```

수정 후:
```kotlin
val FEATURES = listOf("auth", "user", "party", "image", "chat", "rollingpaper", "me")
```

- [ ] **Step 2: 빌드 + ArchUnit 테스트 통과 확인**

Run:
```bash
./gradlew test --tests "com.team2.server.architecture.*"
```
Expected: 모든 ArchUnit 테스트 PASS (활성화된 규칙 기준).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt
git commit -m "chore: ArchUnit FEATURES 목록에 me 추가"
```

---

## Task 2: SupportProperties + yml 키 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/me/config/SupportProperties.kt`
- Modify: `src/main/resources/application.yml` (파일 끝에 섹션 추가)
- Modify: `src/test/resources/application.yml` (파일 끝에 섹션 추가)

- [ ] **Step 1: SupportProperties 클래스 생성**

`src/main/kotlin/com/team2/server/me/config/SupportProperties.kt`:

```kotlin
package com.team2.server.me.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "support")
data class SupportProperties(
    @field:NotBlank
    val chatUrl: String,
)
```

> 참고: `ServerApplication.kt`에 `@ConfigurationPropertiesScan` 이미 적용되어 있어 별도 `@EnableConfigurationProperties` 불필요.

- [ ] **Step 2: 메인 application.yml에 키 추가**

`src/main/resources/application.yml` 끝에 아래 블록을 추가한다 (들여쓰기 없이 최상단 키로):

```yaml

support:
  chat-url: "https://open.kakao.com/o/placeholder"
```

> URL은 일단 placeholder. 실제 운영 URL은 환경별 yml에서 override하거나 추후 별도 커밋으로 교체.

- [ ] **Step 3: 테스트용 application.yml에 키 추가**

`src/test/resources/application.yml` 끝에 아래 블록을 추가한다:

```yaml

support:
  chat-url: "https://open.kakao.com/o/test-support"
```

- [ ] **Step 4: 빌드로 ConfigurationProperties 바인딩 확인**

Run:
```bash
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 컨텍스트 로딩 테스트로 yml 바인딩 확인**

Run:
```bash
./gradlew test --tests "com.team2.server.ServerApplicationTests"
```
Expected: PASS. (애플리케이션 컨텍스트가 SupportProperties 바인딩 실패 없이 로드됨)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/team2/server/me/config/SupportProperties.kt src/main/resources/application.yml src/test/resources/application.yml
git commit -m "feat: 1대1 문의 카카오 오픈채팅 URL 설정 추가"
```

---

## Task 3: MeAccountResponse DTO 작성

**Files:**
- Create: `src/main/kotlin/com/team2/server/me/api/dto/MeAccountResponse.kt`

- [ ] **Step 1: MeAccountResponse 생성**

`src/main/kotlin/com/team2/server/me/api/dto/MeAccountResponse.kt`:

```kotlin
package com.team2.server.me.api.dto

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import java.time.LocalDate

data class MeAccountResponse(
    val nickname: String,
    val provider: AuthProvider,
    val connectedAt: LocalDate,
    val supportChatUrl: String,
) {
    companion object {
        fun from(
            user: User,
            supportChatUrl: String,
        ): MeAccountResponse =
            MeAccountResponse(
                nickname = user.name,
                provider = user.provider,
                connectedAt = user.createdAt.toLocalDate(),
                supportChatUrl = supportChatUrl,
            )
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/team2/server/me/api/dto/MeAccountResponse.kt
git commit -m "feat: 마이페이지 계정 응답 DTO 추가"
```

---

## Task 4: GetMeAccountUseCase (TDD)

**Files:**
- Test: `src/test/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCaseTest.kt`
- Create: `src/main/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCase.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCaseTest.kt`:

```kotlin
package com.team2.server.me.application.usecase

import com.team2.server.auth.FakeUserRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.me.config.SupportProperties
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GetMeAccountUseCaseTest {
    private val supportProperties = SupportProperties(chatUrl = "https://open.kakao.com/o/test")

    @Test
    fun `사용자 정보와 1대1 문의 URL을 묶어 응답 DTO로 반환한다`() {
        val repository = FakeUserRepository()
        val user =
            repository.save(
                User(
                    name = "김이라",
                    birthDay = "03-15",
                    provider = AuthProvider.KAKAO,
                    providerId = "kakao-12345",
                    email = "ira@kakao.local",
                ),
            )
        user.createdAt = LocalDateTime.of(2026, 2, 23, 10, 0)
        val useCase = GetMeAccountUseCase(repository, supportProperties)

        val response = useCase.invoke(user.id)

        assertThat(response.nickname).isEqualTo("김이라")
        assertThat(response.provider).isEqualTo(AuthProvider.KAKAO)
        assertThat(response.connectedAt).isEqualTo(LocalDateTime.of(2026, 2, 23, 10, 0).toLocalDate())
        assertThat(response.supportChatUrl).isEqualTo("https://open.kakao.com/o/test")
    }

    @Test
    fun `userId 에 해당하는 사용자가 없으면 AUTH_USER_NOT_FOUND BusinessException`() {
        val repository = FakeUserRepository()
        val useCase = GetMeAccountUseCase(repository, supportProperties)

        assertThatThrownBy { useCase.invoke(999L) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_USER_NOT_FOUND)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인 (RED)**

Run:
```bash
./gradlew test --tests "com.team2.server.me.application.usecase.GetMeAccountUseCaseTest"
```
Expected: 컴파일 실패 — `GetMeAccountUseCase` 미존재.

- [ ] **Step 3: 최소 구현 작성 (GREEN)**

`src/main/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCase.kt`:

```kotlin
package com.team2.server.me.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.me.api.dto.MeAccountResponse
import com.team2.server.me.config.SupportProperties
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMeAccountUseCase(
    private val userRepository: UserRepository,
    private val supportProperties: SupportProperties,
) {
    @Transactional(readOnly = true)
    fun invoke(userId: Long): MeAccountResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return MeAccountResponse.from(user, supportProperties.chatUrl)
    }
}
```

> 참고: 기존 layered 마이그레이션 컨벤션 (`CreateRealtimePartyUseCase` 등)을 따라 stereotype은 `@Service`. UseCase 클래스에 `@Transactional(readOnly = true)` 선언.

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

Run:
```bash
./gradlew test --tests "com.team2.server.me.application.usecase.GetMeAccountUseCaseTest"
```
Expected: 두 테스트 모두 PASS.

- [ ] **Step 5: ktlint 통과 확인**

Run:
```bash
./gradlew ktlintCheck
```
Expected: BUILD SUCCESSFUL. 실패 시 `./gradlew ktlintFormat` 후 재실행.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCase.kt src/test/kotlin/com/team2/server/me/application/usecase/GetMeAccountUseCaseTest.kt
git commit -m "feat: 마이페이지 계정 조회 UseCase 구현"
```

---

## Task 5: MeAccountController (TDD)

**Files:**
- Test: `src/test/kotlin/com/team2/server/me/api/MeAccountControllerTest.kt`
- Create: `src/main/kotlin/com/team2/server/me/api/MeAccountController.kt`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`src/test/kotlin/com/team2/server/me/api/MeAccountControllerTest.kt`:

```kotlin
package com.team2.server.me.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MeAccountControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            userRepository.deleteAll()
        }

        @Test
        fun `인증 없이 마이페이지 계정 조회 시 401`() {
            mockMvc.get("/api/me/account").andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        fun `유효한 JWT 로 마이페이지 계정 정보를 조회한다`() {
            val user = saveUser("kakao-me-account-1", "me-account-1@kakao.local", name = "김이라")
            user.createdAt = LocalDateTime.of(2026, 2, 23, 10, 0)
            userRepository.saveAndFlush(user)
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/me/account") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value(200) }
                    jsonPath("$.data.nickname") { value("김이라") }
                    jsonPath("$.data.provider") { value("KAKAO") }
                    jsonPath("$.data.connectedAt") { value("2026-02-23") }
                    jsonPath("$.data.supportChatUrl") { value("https://open.kakao.com/o/test-support") }
                }
        }

        @Test
        fun `토큰의 userId 가 DB 에 없으면 401`() {
            val user = saveUser("kakao-me-account-2", "me-account-2@kakao.local", name = "삭제예정")
            val token = tokenProvider.issue(user)
            userRepository.deleteById(user.id)
            userRepository.flush()

            mockMvc
                .get("/api/me/account") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
            name: String = "조회자",
        ): User =
            userRepository.save(
                User(
                    name = name,
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
```

> 참고: `supportChatUrl` 기대값은 `src/test/resources/application.yml`에 박힌 `https://open.kakao.com/o/test-support`와 일치해야 함.

- [ ] **Step 2: 테스트 실패 확인 (RED)**

Run:
```bash
./gradlew test --tests "com.team2.server.me.api.MeAccountControllerTest"
```
Expected: 컴파일 실패 — `MeAccountController` 미존재.

- [ ] **Step 3: 최소 구현 작성 (GREEN)**

`src/main/kotlin/com/team2/server/me/api/MeAccountController.kt`:

```kotlin
package com.team2.server.me.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.me.api.dto.MeAccountResponse
import com.team2.server.me.application.usecase.GetMeAccountUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class MeAccountController(
    private val getMeAccountUseCase: GetMeAccountUseCase,
) {
    @GetMapping("/account")
    fun getAccount(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<MeAccountResponse> = ApiResponse.success(getMeAccountUseCase.invoke(principal.userId))
}
```

- [ ] **Step 4: 테스트 통과 확인 (GREEN)**

Run:
```bash
./gradlew test --tests "com.team2.server.me.api.MeAccountControllerTest"
```
Expected: 세 테스트 모두 PASS.

- [ ] **Step 5: 컨테이너 누수 확인**

Run:
```bash
docker ps -a --filter "label=org.testcontainers"
```
Expected: 결과 0개 (테스트 종료 후 컨테이너 정리됨).

- [ ] **Step 6: ktlint 통과 확인**

Run:
```bash
./gradlew ktlintCheck
```
Expected: BUILD SUCCESSFUL. 실패 시 `./gradlew ktlintFormat` 후 재실행.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/team2/server/me/api/MeAccountController.kt src/test/kotlin/com/team2/server/me/api/MeAccountControllerTest.kt
git commit -m "feat: 마이페이지 계정 조회 API 추가"
```

---

## Task 6: 전체 빌드 + 테스트 통과 확인

**Files:** 변경 없음

- [ ] **Step 1: 전체 테스트 실행**

Run:
```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL. 모든 테스트 PASS.

- [ ] **Step 2: 전체 빌드**

Run:
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL. (ktlint + 컴파일 + 테스트 + jar 생성)

- [ ] **Step 3: 컨테이너 누수 최종 확인**

Run:
```bash
docker ps -a --filter "label=org.testcontainers"
```
Expected: 결과 0개.

- [ ] **Step 4: 커밋이 없으면 종료, 잔여 변경 있으면 점검**

Run:
```bash
git status
```
Expected: working tree clean. 변경 남아 있으면 어느 task에 속하는지 확인 후 적절한 task로 되돌아감.

---

## 완료 후 다음 단계

1. 사용자가 plan 검토 → 승인
2. `team-flow` 스킬 호출 → 이슈 생성 + 정식 feature 브랜치 생성
3. 정식 브랜치에서 cherry-pick 또는 동일 task 순서대로 재실행 → PR 생성

> 본 plan은 `design/my-page-account` 워크트리에서 실행 가능. 단, 최종 PR은 `team-flow`로 생성된 `feature/...` 브랜치에서 진행한다.
