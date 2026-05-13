# Archive List API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/v1/archive`를 추가해서 인증된 사용자가 호스트로 만들었거나 참여자로 가입한 모든 파티를 cursor 페이지네이션으로 조회한다. 비로그인은 200 빈 응답을 받는다.

**Architecture:** `party/` feature 안에 4-레이어 패키지(`api/`, `application/usecase/`)로 신규 코드를 두고, 단일 SQL 조회(`Participant.user_id = :userId`)로 host + 참가자를 모두 커버한다. `ParticipantRepository`에는 메서드 2개만 추가하고 위치는 그대로 둔다(마이그레이션 브랜치가 함께 옮길 예정).

**Tech Stack:** Kotlin 2.x · Spring Boot 3.x · Spring Data JPA(Hibernate) · MySQL · JUnit 5 · MockMvc · Spring Security(JWT) · Springdoc OpenAPI · Gradle Kotlin DSL.

**Spec:** [`../specs/2026-05-07-archive-list-api-design.md`](../specs/2026-05-07-archive-list-api-design.md)

---

## File Structure

```
src/main/kotlin/com/team2/server/
├── party/
│   ├── api/                                                  ◀ 신규 디렉터리
│   │   ├── ArchiveController.kt                              ◀ 신규
│   │   ├── ArchiveApi.kt                                     ◀ 신규
│   │   └── dto/
│   │       ├── ArchiveItemType.kt                            ◀ 신규 (enum)
│   │       ├── ArchiveListResponse.kt                        ◀ 신규
│   │       └── ArchiveListItemResponse.kt                    ◀ 신규
│   ├── application/usecase/                                  ◀ 신규 디렉터리
│   │   └── GetMyArchiveUseCase.kt                            ◀ 신규
│   └── repository/
│       └── ParticipantRepository.kt                          ◀ 메서드 2개 추가
└── auth/
    └── config/
        └── SecurityConfig.kt                                 ◀ permitAll 1줄 추가

src/test/kotlin/com/team2/server/
└── party/
    └── api/
        └── ArchiveControllerTest.kt                          ◀ 신규
```

각 파일의 단일 책임:

| 파일 | 책임 |
|---|---|
| `ArchiveItemType` | `PartyOption → "PARTY"/"PAPER"` enum 매핑 |
| `ArchiveListItemResponse` | 단일 보관함 항목 DTO (`Participant → DTO` 변환) |
| `ArchiveListResponse` | 페이지 응답 DTO (`items`, `nextCursor`, `totalCount`) |
| `ArchiveApi` | Swagger 인터페이스 |
| `ArchiveController` | HTTP 엔드포인트 + 입력 검증 |
| `GetMyArchiveUseCase` | 흐름 — `@Transactional(readOnly = true)`, repository 조회 + DTO 변환 |
| `ParticipantRepository` (확장) | cursor 조회 + count 쿼리 |
| `SecurityConfig` (수정) | `/api/v1/archive` permitAll 등록 |

---

## Tech Notes (구현 전 필독)

- **빌드:** `./gradlew build` (전체) / `./gradlew test --tests "ArchiveControllerTest"` (단일).
- **commit 규칙:** `<type>: <한국어 명사형>`. 예: `feat: 보관함 리스트 API 골격 추가`. 50자 이내.
- **`git add` 규칙:** 파일 개별 지정. `git add -A` 금지. `--no-verify` 금지.
- **인증 토큰:** 테스트에서 `JwtTokenProvider(jwtProperties).issue(user)`로 발급, `Authorization: Bearer $token` 헤더로 전달.
- **DB 정리:** 테스트에서 `@BeforeEach`에 `participantRepository.deleteAll()` 등 직접 호출 (`MePartyControllerTest` 패턴).
- **`@RequestParam`에 `@Min/@Max` 못 씀** (별도 `@Validated` 설정 필요). 따라서 controller에서 manual 검증해 `BusinessException(ErrorCode.INVALID_INPUT)` 던진다.
- **`OffsetDateTime` 직렬화:** Jackson JSR310 기본으로 ISO 8601 + 오프셋이 포함된 형태로 직렬화됨 (`2026-05-12T22:10:00+09:00`). 글로벌 설정 변경 불필요.
- **호스트도 `Participant`에 자동 등록됨** (`GetUpcomingPartiesUseCase`의 동작이 그 가정 위에 있음). 따라서 host vs participant 구분 없이 `Participant.user.id = :userId` 단일 쿼리로 OK.

---

## Task 1: 정적 골격(DTO/Enum/Repository/UseCase/Controller/Security) 추가 — 컴파일만 통과

**목표:** 모든 신규 파일을 생성하고, `SecurityConfig` 한 줄을 추가해서 빌드만 통과시킨다. UseCase 본체는 빈 응답을 즉시 반환한다(시나리오 구현은 다음 task부터).

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchiveItemType.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchiveListItemResponse.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchiveListResponse.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/ArchiveApi.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/ArchiveController.kt`
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/GetMyArchiveUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`

- [ ] **Step 1.1: `ArchiveItemType` enum 생성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/dto/ArchiveItemType.kt
package com.team2.server.party.api.dto

import com.team2.server.party.entity.PartyOption

enum class ArchiveItemType {
    PARTY,
    PAPER,
    ;

    companion object {
        fun from(option: PartyOption): ArchiveItemType =
            when (option) {
                PartyOption.REALTIME -> PARTY
                PartyOption.PAPER_ONLY -> PAPER
            }
    }
}
```

- [ ] **Step 1.2: `ArchiveListItemResponse` 생성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/dto/ArchiveListItemResponse.kt
package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.entity.Participant
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneOffset

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 항목")
data class ArchiveListItemResponse(
    @Schema(description = "항목 ID (participant.id)", example = "1024")
    val id: String,
    @Schema(description = "항목 타입", allowableValues = ["PARTY", "PAPER"], example = "PARTY")
    val type: ArchiveItemType,
    @Schema(description = "파티 이름. 없으면 빈 문자열", example = "김루카 생일 파티")
    val title: String,
    @Schema(description = "파티 종료 시각 (KST 오프셋)", example = "2026-05-12T22:10:00+09:00")
    val date: OffsetDateTime,
) {
    companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun from(participant: Participant): ArchiveListItemResponse {
            val party = participant.party
            return ArchiveListItemResponse(
                id = participant.id.toString(),
                type = ArchiveItemType.from(party.partyOption),
                title = party.name ?: "",
                date = party.endedAt().atOffset(KST),
            )
        }
    }
}
```

- [ ] **Step 1.3: `ArchiveListResponse` 생성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/dto/ArchiveListResponse.kt
package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 리스트 응답")
data class ArchiveListResponse(
    @Schema(description = "보관함 항목")
    val items: List<ArchiveListItemResponse>,
    @Schema(description = "다음 페이지 cursor. 없으면 null", nullable = true, example = "1024")
    val nextCursor: String?,
    @Schema(description = "보관함 전체 개수 (헤더 표시용)", example = "37")
    val totalCount: Long,
) {
    companion object {
        val EMPTY: ArchiveListResponse =
            ArchiveListResponse(items = emptyList(), nextCursor = null, totalCount = 0)
    }
}
```

- [ ] **Step 1.4: `ParticipantRepository`에 메서드 2개 추가**

기존 파일 끝(`}` 직전)에 메서드 2개 추가:

```kotlin
// src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt
// 기존 import에 다음 추가:
//   import org.springframework.data.domain.Pageable
//   (org.springframework.data.jpa.repository.Query 는 이미 import 됨)

// interface ParticipantRepository : JpaRepository<Participant, Long> { ... 의 }  직전에 추가:

    @Query(
        """
        SELECT p
        FROM Participant p
        JOIN FETCH p.party party
        WHERE p.user.id = :userId
          AND (:cursor IS NULL OR p.id < :cursor)
        ORDER BY p.id DESC
        """,
    )
    fun findArchiveByUserId(
        userId: Long,
        cursor: Long?,
        pageable: Pageable,
    ): List<Participant>

    @Query("SELECT COUNT(p) FROM Participant p WHERE p.user.id = :userId")
    fun countArchiveByUserId(userId: Long): Long
```

- [ ] **Step 1.5: `GetMyArchiveUseCase` 골격 생성 (빈 응답만 반환)**

```kotlin
// src/main/kotlin/com/team2/server/party/application/usecase/GetMyArchiveUseCase.kt
package com.team2.server.party.application.usecase

import com.team2.server.party.api.dto.ArchiveListItemResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import com.team2.server.party.repository.ParticipantRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyArchiveUseCase(
    private val participantRepository: ParticipantRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(
        userId: Long?,
        cursor: Long?,
        size: Int,
    ): ArchiveListResponse {
        if (userId == null) return ArchiveListResponse.EMPTY

        val rows =
            participantRepository.findArchiveByUserId(
                userId = userId,
                cursor = cursor,
                pageable = PageRequest.of(0, size + 1),
            )
        val hasNext = rows.size > size
        val pageItems = rows.take(size)
        val items = pageItems.map(ArchiveListItemResponse::from)
        val nextCursor = if (hasNext) pageItems.last().id.toString() else null
        val totalCount = participantRepository.countArchiveByUserId(userId)

        return ArchiveListResponse(items = items, nextCursor = nextCursor, totalCount = totalCount)
    }
}
```

- [ ] **Step 1.6: `ArchiveApi` 인터페이스 생성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/ArchiveApi.kt
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.common.swagger.ValidationErrorResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Archive", description = "보관함 API")
interface ArchiveApi {
    @Operation(
        summary = "보관함 리스트 조회",
        description = "사용자가 호스트로 만들었거나 참여한 파티 목록을 최신순으로 조회한다. 비로그인은 200 빈 응답을 반환한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "보관함 조회 성공")
    @ValidationErrorResponse
    @InternalServerErrorResponse
    fun getArchive(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "마지막으로 받은 항목의 id. 첫 페이지면 생략") cursor: Long?,
        @Parameter(description = "페이지 크기. 1~50, 기본 20") size: Int,
    ): ApiResponse<ArchiveListResponse>
}
```

- [ ] **Step 1.7: `ArchiveController` 생성 (manual validation 포함)**

```kotlin
// src/main/kotlin/com/team2/server/party/api/ArchiveController.kt
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import com.team2.server.party.application.usecase.GetMyArchiveUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(
    private val getMyArchiveUseCase: GetMyArchiveUseCase,
) : ArchiveApi {
    @GetMapping
    override fun getArchive(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
    ): ApiResponse<ArchiveListResponse> {
        validateCursor(cursor)
        validateSize(size)
        return ApiResponse.success(getMyArchiveUseCase.invoke(principal?.userId, cursor, size))
    }

    private fun validateCursor(cursor: Long?) {
        if (cursor != null && cursor < 1) throw BusinessException(ErrorCode.INVALID_INPUT)
    }

    private fun validateSize(size: Int) {
        if (size < MIN_SIZE || size > MAX_SIZE) throw BusinessException(ErrorCode.INVALID_INPUT)
    }

    companion object {
        private const val DEFAULT_SIZE = 20
        private const val MIN_SIZE = 1
        private const val MAX_SIZE = 50
    }
}
```

- [ ] **Step 1.8: `SecurityConfig`에 permitAll 한 줄 추가**

기존 `auth.requestMatchers(...).permitAll()` 블록 마지막 항목 뒤에 한 줄 추가:

```kotlin
// src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
// 기존 permitAll 라인들 다음, anyRequest().authenticated() 직전에 추가:

                auth.requestMatchers(HttpMethod.GET, "/api/v1/archive").permitAll()
```

- [ ] **Step 1.9: 빌드 확인 (테스트는 아직 없음)**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.10: 커밋**

```bash
git add \
  src/main/kotlin/com/team2/server/party/api/ArchiveApi.kt \
  src/main/kotlin/com/team2/server/party/api/ArchiveController.kt \
  src/main/kotlin/com/team2/server/party/api/dto/ArchiveItemType.kt \
  src/main/kotlin/com/team2/server/party/api/dto/ArchiveListItemResponse.kt \
  src/main/kotlin/com/team2/server/party/api/dto/ArchiveListResponse.kt \
  src/main/kotlin/com/team2/server/party/application/usecase/GetMyArchiveUseCase.kt \
  src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt \
  src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
git commit -m "feat: 보관함 리스트 API 골격 추가"
```

---

## Task 2: 비로그인 / 빈 보관함 시나리오

**목표:** 비로그인 호출과 보관함 0건 케이스 모두 200 빈 응답을 반환하는지 검증.

**Files:**
- Create: `src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt`

- [ ] **Step 2.1: 통합 테스트 클래스 + 첫 두 테스트 작성**

```kotlin
// src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        @Test
        fun `비로그인 호출 시 200과 빈 보관함 응답을 반환한다`() {
            mockMvc.get("/api/v1/archive").andExpect {
                status { isOk() }
                jsonPath("$.data.items.length()") { value(0) }
                jsonPath("$.data.nextCursor") { value(nullValue()) }
                jsonPath("$.data.totalCount") { value(0) }
            }
        }

        @Test
        fun `인증된 사용자의 보관함이 비어있으면 빈 응답을 반환한다`() {
            val user = saveUser("kakao-archive-empty", "archive-empty@kakao.local")
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(0) }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "조회자",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
```

- [ ] **Step 2.2: 테스트 실행 (이번엔 통과해야 함 — Task 1에서 이미 빈 응답 구현 완료)**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 2 tests passed.

> 이 task는 RED 없이 GREEN으로 시작 — Task 1에서 빈 응답 동작을 함께 구현했기 때문. 두 시나리오의 동작이 정확히 spec대로인지 검증하는 안전망 역할.

- [ ] **Step 2.3: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
git commit -m "test: 보관함 비로그인 및 빈 응답 검증 추가"
```

---

## Task 3: 단일 항목 조회 — type 매핑, title null 처리, date 형식

**목표:** 호스트가 만든 REALTIME 파티 1건이 `type: "PARTY"`로 매핑되고, PAPER_ONLY 파티는 `"PAPER"`, `name == null`이면 `title: ""`, `date`가 KST 오프셋 포함 ISO 8601 형식인지 검증.

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt`

- [ ] **Step 3.1: 픽스처 헬퍼와 테스트 3개 추가**

테스트 클래스 안에 헬퍼 + 테스트 추가. import 보강:

```kotlin
// 추가 import:
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.RealtimeParty
import org.hamcrest.Matchers.matchesPattern
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
```

테스트 메서드 추가 (saveUser 위에 추가):

```kotlin
        @Test
        fun `REALTIME 파티는 type PARTY로 매핑된다`() {
            val user = saveUser("kakao-archive-party", "archive-party@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "김루카 생일 파티",
                        celebrantNickname = "김루카",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            val participant = saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].id") { value(participant.id.toString()) }
                    jsonPath("$.data.items[0].type") { value("PARTY") }
                    jsonPath("$.data.items[0].title") { value("김루카 생일 파티") }
                    jsonPath("$.data.items[0].date") {
                        value(
                            party
                                .endedAt()
                                .atOffset(java.time.ZoneOffset.ofHours(9))
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        )
                    }
                    jsonPath("$.data.totalCount") { value(1) }
                }
        }

        @Test
        fun `PAPER_ONLY 파티는 type PAPER로 매핑된다`() {
            val user = saveUser("kakao-archive-paper", "archive-paper@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        name = "축하해요",
                        celebrantNickname = "민수",
                        startedAt = now.toLocalDate().plusDays(1).atStartOfDay(),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].type") { value("PAPER") }
                }
        }

        @Test
        fun `party name이 null이면 title은 빈 문자열로 응답한다`() {
            val user = saveUser("kakao-archive-noname", "archive-noname@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = null,
                        celebrantNickname = "이름없음",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].title") { value("") }
                }
        }

        private fun saveParty(
            party: Party,
            createdAt: LocalDateTime,
        ): Party {
            val saved = partyRepository.saveAndFlush(party)
            saved.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS)
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveParticipant(
            party: Party,
            user: User,
            createdAt: LocalDateTime,
        ): Participant {
            val saved =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = user),
                )
            saved.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS)
            return participantRepository.saveAndFlush(saved)
        }
```

- [ ] **Step 3.2: 테스트 실행**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 모든 테스트 PASS.

만약 `date` 직렬화 형식이 ISO_OFFSET_DATE_TIME과 다르면 (예: 밀리초 포함), Jackson 설정 또는 매처를 조정. 가장 빠른 fallback:

```kotlin
jsonPath("$.data.items[0].date") {
    value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*\\+09:00"))
}
```

- [ ] **Step 3.3: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
git commit -m "test: 보관함 단건 조회 매핑 검증 추가"
```

---

## Task 4: 다건 + 정렬 (`participant.id DESC`)

**목표:** 호스트로 만든 1건 + 참여한 2건 (총 3건)이 모두 조회되고 `participant.id DESC` 정렬을 따르는지 검증. 이 테스트가 통과하면 spec의 "host도 participant 단일 쿼리로 커버" 가정이 검증됨.

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt`

- [ ] **Step 4.1: 다건 정렬 테스트 추가**

```kotlin
        @Test
        fun `host로 만든 파티와 참여한 파티가 함께 조회되고 participant id DESC로 정렬된다`() {
            val user = saveUser("kakao-archive-multi", "archive-multi@kakao.local")
            val other = saveUser("kakao-archive-other", "archive-other@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val hostParty =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "내 파티",
                        celebrantNickname = "본인",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(3),
                )
            val joinedPaperParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = other.id,
                        name = "친구 파티",
                        celebrantNickname = "친구",
                        startedAt = now.toLocalDate().plusDays(1).atStartOfDay(),
                    ),
                    now.minusHours(2),
                )
            val joinedRealtimeParty =
                saveParty(
                    RealtimeParty(
                        ownerId = other.id,
                        name = "친구 라이브",
                        celebrantNickname = "친구2",
                        startedAt = now.plusHours(4),
                    ),
                    now.minusHours(1),
                )

            // 가입 순서: hostParty 먼저, 그다음 joinedPaperParty, 마지막 joinedRealtimeParty
            // 정렬: participant.id DESC → joinedRealtimeParty, joinedPaperParty, hostParty
            saveParticipant(hostParty, user, now.minusHours(3))
            saveParticipant(joinedPaperParty, user, now.minusHours(2))
            saveParticipant(joinedRealtimeParty, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(3) }
                    jsonPath("$.data.items[0].title") { value("친구 라이브") }
                    jsonPath("$.data.items[1].title") { value("친구 파티") }
                    jsonPath("$.data.items[2].title") { value("내 파티") }
                    jsonPath("$.data.totalCount") { value(3) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                }
        }
```

- [ ] **Step 4.2: 테스트 실행**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 모든 테스트 PASS.

만약 host의 `Participant` 레코드가 자동 생성되지 않는 환경(예: 다른 테스트 픽스처가 다르게 만들 때)이면 위 테스트는 hostParty가 누락됨. 본 테스트는 `saveParticipant(hostParty, user, ...)`로 직접 등록하므로 안전.

- [ ] **Step 4.3: 다른 사용자의 파티는 보관함에 안 잡히는지 검증 추가**

```kotlin
        @Test
        fun `다른 사용자가 가입한 파티는 보관함에 포함되지 않는다`() {
            val user = saveUser("kakao-archive-isolation", "archive-isolation@kakao.local")
            val other = saveUser("kakao-archive-other2", "archive-other2@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val otherParty =
                saveParty(
                    RealtimeParty(
                        ownerId = other.id,
                        name = "남의 파티",
                        celebrantNickname = "타인",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(otherParty, other, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.totalCount") { value(0) }
                }
        }
```

- [ ] **Step 4.4: 테스트 실행 + 커밋**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 모든 테스트 PASS.

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
git commit -m "test: 보관함 다건 정렬 및 사용자 격리 검증 추가"
```

---

## Task 5: cursor 페이지네이션 — `nextCursor` 동작 검증

**목표:** size + 1 fetch 패턴이 정확히 동작하는지, 마지막 페이지에서 `nextCursor`가 null인지, 항목 수가 정확히 size인 케이스에서도 nextCursor가 null인지, 큰 cursor 값(없는 id)은 빈 응답이 나오는지 검증.

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt`

- [ ] **Step 5.1: 페이지네이션 테스트 3개 추가**

```kotlin
        @Test
        fun `size보다 항목이 많으면 nextCursor가 마지막 항목 id로 설정된다`() {
            val user = saveUser("kakao-archive-page1", "archive-page1@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val participants =
                (1..3).map { idx ->
                    val party =
                        saveParty(
                            RealtimeParty(
                                ownerId = user.id,
                                name = "파티 $idx",
                                celebrantNickname = "주인공 $idx",
                                startedAt = now.plusHours(idx.toLong()),
                            ),
                            now.minusHours(idx.toLong()),
                        )
                    saveParticipant(party, user, now.minusMinutes(idx * 10L))
                }

            mockMvc
                .get("/api/v1/archive?size=2") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(2) }
                    jsonPath("$.data.items[0].id") { value(participants[2].id.toString()) }
                    jsonPath("$.data.items[1].id") { value(participants[1].id.toString()) }
                    jsonPath("$.data.nextCursor") { value(participants[1].id.toString()) }
                    jsonPath("$.data.totalCount") { value(3) }
                }
        }

        @Test
        fun `cursor로 다음 페이지를 받으면 남은 항목과 nextCursor null을 응답한다`() {
            val user = saveUser("kakao-archive-page2", "archive-page2@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val participants =
                (1..3).map { idx ->
                    val party =
                        saveParty(
                            RealtimeParty(
                                ownerId = user.id,
                                name = "파티 $idx",
                                celebrantNickname = "주인공 $idx",
                                startedAt = now.plusHours(idx.toLong()),
                            ),
                            now.minusHours(idx.toLong()),
                        )
                    saveParticipant(party, user, now.minusMinutes(idx * 10L))
                }

            // participants[1].id 이전 항목만 남음 → participants[0]
            mockMvc
                .get("/api/v1/archive?size=2&cursor=${participants[1].id}") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].id") { value(participants[0].id.toString()) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(3) }
                }
        }

        @Test
        fun `항목 수가 정확히 size와 같으면 nextCursor는 null이다`() {
            val user = saveUser("kakao-archive-exact", "archive-exact@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            (1..2).forEach { idx ->
                val party =
                    saveParty(
                        RealtimeParty(
                            ownerId = user.id,
                            name = "파티 $idx",
                            celebrantNickname = "주인공 $idx",
                            startedAt = now.plusHours(idx.toLong()),
                        ),
                        now.minusHours(idx.toLong()),
                    )
                saveParticipant(party, user, now.minusMinutes(idx * 10L))
            }

            mockMvc
                .get("/api/v1/archive?size=2") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(2) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                }
        }

        @Test
        fun `존재하지 않는 cursor를 보내면 빈 items와 nextCursor null을 응답한다`() {
            val user = saveUser("kakao-archive-largecur", "archive-largecur@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "유일",
                        celebrantNickname = "주인공",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            // cursor < participant.id 인 항목이 없는 매우 작은 cursor (1)
            mockMvc
                .get("/api/v1/archive?cursor=1") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(1) }
                }
        }
```

- [ ] **Step 5.2: 테스트 실행 + 커밋**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 모든 테스트 PASS.

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
git commit -m "test: 보관함 cursor 페이지네이션 검증 추가"
```

---

## Task 6: 입력값 검증 (`size`, `cursor` invalid)

**목표:** controller의 manual validation이 정상 동작 — `size = 0`, `size = 51`, `cursor = 0`, `cursor = -1`, `cursor = "abc"` 시 400 응답을 반환.

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt`

- [ ] **Step 6.1: validation 테스트 추가**

```kotlin
        @Test
        fun `size가 0이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?size=0").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `size가 51이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?size=51").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 0이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=0").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 음수면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=-1").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 숫자가 아니면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=abc").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }
```

- [ ] **Step 6.2: 테스트 실행**

Run: `./gradlew test --tests "com.team2.server.party.api.ArchiveControllerTest"`
Expected: 모든 테스트 PASS.

`MethodArgumentTypeMismatchException`(예: `cursor=abc`)는 `GlobalExceptionHandler`가 이미 400 `INVALID_INPUT`으로 변환하므로 별도 처리 불필요.

`size = 0` / `size = 51` / `cursor = 0` / `cursor = -1`은 controller의 manual 검증이 처리.

- [ ] **Step 6.3: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt
git commit -m "test: 보관함 size cursor 검증 케이스 추가"
```

---

## Task 7: 최종 빌드 + 정합성 점검

**목표:** 전체 테스트와 정적 분석을 돌려 문제 없음을 확인하고, 스펙 정합성을 마지막으로 검토한다.

- [ ] **Step 7.1: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7.2: 전체 빌드 (ktlint 등 정적 분석 포함)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

ktlint 위반이 발생하면 `./gradlew ktlintFormat` 후 다시 `./gradlew build`. 차이 발생 시:

```bash
git add -p   # 변경된 부분 검토 후 staging
git commit -m "chore: 보관함 ktlint 포맷"
```

- [ ] **Step 7.3: spec 정합성 마지막 점검 — 본 plan에 없는 시나리오 누락 확인**

다음 항목이 모두 테스트로 커버되는지 머릿속으로 매칭:

| spec 9 테스트 케이스 | 매칭 task |
|---|---|
| T1 비로그인 | Task 2 |
| T2 보관함 비어 있음 | Task 2 |
| T3 host 1건 + participant 2건 | Task 4 |
| T4 REALTIME → "PARTY" | Task 3 |
| T5 PAPER_ONLY → "PAPER" | Task 3 |
| T6 `name == null` → `""` | Task 3 |
| T7 size=2, 3건 → 2건 + nextCursor | Task 5 |
| T8 nextCursor로 두 번째 페이지 | Task 5 |
| T9 size=2, 정확히 2건 | Task 5 |
| T10 totalCount 페이지마다 동일 | Task 4 (`totalCount`= 3), Task 5 (`totalCount`=3) — 두 페이지 호출 + 동일값 직접 검증은 약간 약함. 보강 옵션이지만 plan scope 외. |
| T11 size 0/51 | Task 6 |
| T12 cursor 0/-1/"abc" | Task 6 |
| T13 cursor 큰 값 | Task 5 |
| T14 date 형식 +09:00 | Task 3 (jsonPath value 검증) |

T10의 직접적인 "두 페이지 동일값" 검증은 누락 — 단, Task 4와 Task 5에서 `totalCount`를 모든 케이스에서 검증하므로 페이지마다 동일값임은 간접 확인됨. 추가 테스트가 필요하다고 판단되면 Task 5에 한 줄 보강.

- [ ] **Step 7.4: PR 생성 전 최종 git 상태 확인**

Run: `git log --oneline develop..HEAD`
Expected: 본 plan의 커밋들 + spec 커밋(`docs: 보관함 리스트 API 설계 추가`).

```
?  feat: 보관함 리스트 API 골격 추가
?  test: 보관함 비로그인 및 빈 응답 검증 추가
?  test: 보관함 단건 조회 매핑 검증 추가
?  test: 보관함 다건 정렬 및 사용자 격리 검증 추가
?  test: 보관함 cursor 페이지네이션 검증 추가
?  test: 보관함 size cursor 검증 케이스 추가
?  docs: 보관함 리스트 API 설계 추가
```

- [ ] **Step 7.5: PR 생성은 사용자 확인 후 — `/team-pr` 또는 수동**

이 plan은 PR 생성을 자동화하지 않는다. 사용자 검토 후 `/team-pr` 스킬 또는 수동으로 PR 생성.

---

## 자가 검토 (plan 작성자)

### 1. Spec 커버리지

| spec 섹션 | plan 매칭 | 비고 |
|---|---|---|
| 1. API 스펙 | Task 1, 2, 3, 6 | 요청/응답 필드 모두 검증 |
| 2. 패키지 구조 | Task 1.1~1.7 | 8 신규 파일 + 1 수정 |
| 3. 데이터 흐름 | Task 1.5 (UseCase) | size+1 fetch + count 별도 |
| 4. Repository 메서드 | Task 1.4 | JPA `@Query` 2개 |
| 5. UseCase 형태 | Task 1.5 | 60줄 이내, 의존성 1개 |
| 6. Controller / API | Task 1.6, 1.7 | manual validation 포함 |
| 7. DTO 정의 | Task 1.1, 1.2, 1.3 | 3개 파일 |
| 8. 경계 케이스 | Task 2~6 분산 | 모두 매칭 |
| 9. 테스트 계획 T1~T14 | Task 2~6 분산 | 위 매칭표 참조 |
| 10. 영향 범위 | File Structure 섹션 | 9개 파일, 일치 |
| 11. PR 분리 | 단일 PR | OK |

### 2. Placeholder 스캔

- "TBD"/"TODO"/"implement later" — 없음
- "appropriate error handling" — 없음 (manual validation 명시)
- 빈 코드 블록 — 없음
- "similar to Task N" — 없음 (코드 반복 명시)

### 3. 타입 일관성

- `ArchiveListItemResponse.id` — 모든 task에서 `participant.id.toString()` 일관
- `ArchiveListResponse.nextCursor` — `pageItems.last().id.toString()` 또는 `null` 일관
- `ArchiveItemType.from(PartyOption)` — Task 1.1에서 정의, Task 1.2에서 사용
- `ParticipantRepository.findArchiveByUserId` 시그니처 — Task 1.4 정의, Task 1.5에서 동일 시그니처 호출
- `BusinessException(ErrorCode.INVALID_INPUT)` — Task 1.7과 Task 6에서 일관

### 4. SecurityConfig 한 줄 누락 위험

Task 1.8이 빠지면 모든 시나리오가 401로 떨어짐. Task 1 step 별로 명시. 빌드 후 임의로 controller만 호출해도 401이면 SecurityConfig 추가 누락 가능성 의심.

### 5. 이슈 노트

- `MePartyControllerTest`는 `@BeforeEach`에서 `partyInviteRepository.deleteAll()`도 호출함. 본 plan은 보관함이 invite 테이블을 건드리지 않으므로 생략. 만약 실행 시 외래 키 제약 오류가 발생하면 `partyInviteRepository.deleteAll()`을 setUp에 추가.
- `OffsetDateTime` 직렬화 형식이 환경 따라 미세하게 다를 수 있음. Task 3.2의 fallback 매처 참고.
