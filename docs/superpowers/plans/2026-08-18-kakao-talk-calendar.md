# 카카오 톡캘린더 일정 등록 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인한 파티 멤버가 파티 시작 전에 해당 파티 일정을 자기 카카오톡 캘린더에 등록·갱신할 수 있는 엔드포인트를 만든다.

**Architecture:** 새 `calendar` feature 패키지에 레이어드 구조(api → application.usecase → application.service/port → domain, infrastructure가 port 구현)를 세운다. 카카오 액세스 토큰은 저장하지 않고 클라이언트가 헤더로 매 요청 전달한다. 카카오 호출은 `RestClient` 기반 어댑터가 담당하고, 파티 조회·멤버 검증은 party feature의 Service를 호출하는 별도 어댑터가 담당한다.

**Tech Stack:** Kotlin 2.3, Spring Boot 4.0.5 (webmvc, data-jpa, security), Jackson 3 (`tools.jackson.databind.ObjectMapper`), Flyway, JUnit5 + mockito-kotlin + MockRestServiceServer + Testcontainers(MySQL)

**Spec:** `docs/superpowers/specs/2026-08-18-kakao-talk-calendar-design.md`

## Global Constraints

- 커밋 메시지는 `<type>: <한국어 명사형 설명>`, 50자 이내, 마침표 없음. scope 없음. 영문 메시지 금지.
- `git add -A` / `git add .` 금지 — 파일을 개별 지정한다.
- `--no-verify` 금지. `main` / `develop` 직접 커밋 금지 (작업 브랜치: `feature/kakao-talk-calendar`).
- `@Transactional`은 UseCase 클래스에만 선언한다. Service에는 금지.
- Service는 다른 Service를 호출하지 않는다. UseCase는 60줄 이내, 생성자 의존성 5개 이내.
- Domain은 application/api/infrastructure와 Spring Data에 의존하지 않는다 (ArchUnit `LayerDependencyTest`가 활성 상태로 검증).
- `calendar.domain`은 `party` 패키지를 import 하지 않는다. party의 enum이 필요하면 calendar 자체 enum으로 매핑한다.
- 테스트는 `docs/testing-rules.md`를 따른다. `@MockitoBean` / `@TestPropertySource` / `@ActiveProfiles` 사용 금지 (Spring 컨텍스트 캐시 분리). MockMvc 통합 테스트는 반드시 `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration::class)` 조합.
- 새 마이그레이션 버전은 `V14` (기존 최신은 `V13`).
- 검증 명령: `./gradlew test --tests "<FQCN>"`, 전체는 `./gradlew build`.

---

### Task 1: 등록 이력 저장소

파티-사용자별 카카오 일정 등록 이력을 저장하는 엔티티·테이블·리포지토리를 만든다. UNIQUE 제약이 멱등성의 실제 방어선이다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarProvider.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/domain/entity/CalendarRegistration.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepository.kt`
- Create: `src/main/resources/db/migration/V14__create_calendar_registration.sql`
- Modify: `src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepositoryTest.kt`

**Interfaces:**
- Consumes: `com.team2.server.common.persistence.BaseEntity` (id / createdAt / updatedAt 제공)
- Produces:
  - `enum class CalendarProvider { KAKAO_TALK }`
  - `class CalendarRegistration(userId: Long, partyId: Long, provider: CalendarProvider, eventId: String? = null)` — `val userId`, `val partyId`, `val provider`, `var eventId: String?` (private set), `fun linkEvent(eventId: String)`
  - `interface CalendarRegistrationRepository : JpaRepository<CalendarRegistration, Long>` — `fun findByUserIdAndPartyIdAndProvider(userId: Long, partyId: Long, provider: CalendarProvider): CalendarRegistration?`
  - 제약 이름 상수: `uk_calendar_registration_user_party_provider`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepositoryTest.kt`

```kotlin
package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.support.JpaSliceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CalendarRegistrationRepositoryTest
    @Autowired
    constructor(
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
    ) : JpaSliceTestSupport() {
        @Test
        fun `userId partyId provider 로 등록 이력을 조회한다`() {
            calendarRegistrationRepository.save(
                CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
            )

            val found =
                calendarRegistrationRepository.findByUserIdAndPartyIdAndProvider(
                    userId = 1L,
                    partyId = 2L,
                    provider = CalendarProvider.KAKAO_TALK,
                )

            assertEquals(1L, found?.userId)
            assertEquals(2L, found?.partyId)
            assertNull(found?.eventId)
        }

        @Test
        fun `같은 사용자 파티 provider 조합은 두 번 저장할 수 없다`() {
            calendarRegistrationRepository.saveAndFlush(
                CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
            )

            assertFailsWith<DataIntegrityViolationException> {
                calendarRegistrationRepository.saveAndFlush(
                    CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
                )
            }
        }

        @Test
        fun `linkEvent 로 카카오 일정 ID를 채운다`() {
            val saved =
                calendarRegistrationRepository.saveAndFlush(
                    CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
                )

            saved.linkEvent("event-1")
            calendarRegistrationRepository.flush()

            assertEquals("event-1", calendarRegistrationRepository.findById(saved.id).get().eventId)
        }
    }
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepositoryTest"`
Expected: 컴파일 실패 — `CalendarRegistration`, `CalendarProvider`, `CalendarRegistrationRepository` unresolved reference

- [ ] **Step 3: enum과 엔티티를 작성한다**

`src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarProvider.kt`

```kotlin
package com.team2.server.calendar.domain.vo

enum class CalendarProvider {
    KAKAO_TALK,
}
```

`src/main/kotlin/com/team2/server/calendar/domain/entity/CalendarRegistration.kt`

```kotlin
package com.team2.server.calendar.domain.entity

import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "calendar_registration",
    uniqueConstraints = [
        UniqueConstraint(
            name = CalendarRegistration.UK_USER_PARTY_PROVIDER,
            columnNames = ["user_id", "party_id", "provider"],
        ),
    ],
)
class CalendarRegistration(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "party_id", nullable = false)
    val partyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: CalendarProvider,
    eventId: String? = null,
) : BaseEntity() {
    @Column(name = "event_id", length = 100)
    final var eventId: String? = eventId
        private set

    fun linkEvent(eventId: String) {
        this.eventId = eventId
    }

    companion object {
        const val UK_USER_PARTY_PROVIDER = "uk_calendar_registration_user_party_provider"
    }
}
```

- [ ] **Step 4: 리포지토리를 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepository.kt`

```kotlin
package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarRegistrationRepository : JpaRepository<CalendarRegistration, Long> {
    fun findByUserIdAndPartyIdAndProvider(
        userId: Long,
        partyId: Long,
        provider: CalendarProvider,
    ): CalendarRegistration?
}
```

- [ ] **Step 5: Flyway 마이그레이션을 작성한다**

`src/main/resources/db/migration/V14__create_calendar_registration.sql`

```sql
create table calendar_registration (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    party_id bigint not null,
    provider varchar(20) not null,
    event_id varchar(100) null,
    primary key (id),
    constraint uk_calendar_registration_user_party_provider unique (user_id, party_id, provider)
);
```

- [ ] **Step 6: ArchUnit feature 목록에 calendar 를 추가한다**

`src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt` 의 `FEATURES` 를 아래로 교체한다.

```kotlin
    val FEATURES = listOf("auth", "user", "party", "chat", "rollingpaper", "me", "burstgame", "calendar")
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepositoryTest" --tests "com.team2.server.architecture.*"`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarProvider.kt \
        src/main/kotlin/com/team2/server/calendar/domain/entity/CalendarRegistration.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepository.kt \
        src/main/resources/db/migration/V14__create_calendar_registration.sql \
        src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt \
        src/test/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepositoryTest.kt
git commit -m "feat: 캘린더 등록 이력 엔티티와 테이블 추가"
```

---

### Task 2: 일정 내용 조립 정책

파티 정보로 캘린더 일정의 제목·시작·종료·설명을 만드는 도메인 규칙. 외부 의존이 없는 순수 함수라 단위 테스트만으로 완결된다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/domain/vo/CelebrationKind.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarEvent.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicy.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicyTest.kt`

**Interfaces:**
- Consumes: 없음 (순수 Kotlin + `java.time`)
- Produces:
  - `enum class CelebrationKind(val partyLabel: String) { BIRTHDAY("생일 파티"), JOB_CHANGE("이직 축하 파티"), WEDDING("결혼 축하 파티") }`
  - `data class CalendarEvent(val title: String, val startAt: LocalDateTime, val endAt: LocalDateTime, val description: String)`
  - `object PartyCalendarEventPolicy { fun compose(kind: CelebrationKind, celebrantName: String?, startedAt: LocalDateTime, inviteUrl: String?): CalendarEvent }`
  - `PartyCalendarEventPolicy.DURATION_MINUTES = 30L`, `MAX_TITLE_LENGTH = 50`

- [ ] **Step 1: 실패하는 정책 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicyTest.kt`

```kotlin
package com.team2.server.calendar.domain.policy

import com.team2.server.calendar.domain.vo.CelebrationKind
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyCalendarEventPolicyTest {
    private val startedAt = LocalDateTime.of(2026, 8, 20, 19, 0)

    @Test
    fun `주인공 이름으로 제목을 만든다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("지민님의 생일 파티", event.title)
    }

    @Test
    fun `파티 목적에 따라 제목 문구가 달라진다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.WEDDING,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("지민님의 결혼 축하 파티", event.title)
    }

    @Test
    fun `주인공 이름이 없으면 파티 종류만 제목으로 쓴다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.JOB_CHANGE,
                celebrantName = "  ",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("이직 축하 파티", event.title)
    }

    @Test
    fun `제목은 50자를 넘지 않는다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "가".repeat(80),
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals(50, event.title.length)
        assertTrue(event.title.startsWith("가가가"))
    }

    @Test
    fun `종료 시각은 시작 30분 뒤다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals(startedAt, event.startAt)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 30), event.endAt)
    }

    @Test
    fun `초대 링크가 있으면 설명에 넣는다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = "https://example.com/invite/abc",
            )

        assertEquals("초대 링크: https://example.com/invite/abc", event.description)
    }

    @Test
    fun `초대 링크가 없으면 설명은 빈 문자열이다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("", event.description)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.domain.policy.PartyCalendarEventPolicyTest"`
Expected: 컴파일 실패 — `PartyCalendarEventPolicy`, `CelebrationKind` unresolved reference

- [ ] **Step 3: vo 두 개를 작성한다**

`src/main/kotlin/com/team2/server/calendar/domain/vo/CelebrationKind.kt`

```kotlin
package com.team2.server.calendar.domain.vo

/**
 * 캘린더 일정 제목에 쓰는 파티 종류.
 *
 * party feature 의 PartyPurpose 를 그대로 쓰지 않는 이유는 calendar.domain 이
 * 다른 feature 에 의존하지 않게 하기 위함이다. 매핑은 infrastructure 어댑터가 한다.
 */
enum class CelebrationKind(
    val partyLabel: String,
) {
    BIRTHDAY("생일 파티"),
    JOB_CHANGE("이직 축하 파티"),
    WEDDING("결혼 축하 파티"),
}
```

`src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarEvent.kt`

```kotlin
package com.team2.server.calendar.domain.vo

import java.time.LocalDateTime

data class CalendarEvent(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val description: String,
)
```

- [ ] **Step 4: 정책을 작성한다**

`src/main/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicy.kt`

```kotlin
package com.team2.server.calendar.domain.policy

import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.calendar.domain.vo.CelebrationKind
import java.time.LocalDateTime

object PartyCalendarEventPolicy {
    const val DURATION_MINUTES = 30L

    /** 카카오 톡캘린더 일정 제목 제한. */
    const val MAX_TITLE_LENGTH = 50

    fun compose(
        kind: CelebrationKind,
        celebrantName: String?,
        startedAt: LocalDateTime,
        inviteUrl: String?,
    ): CalendarEvent =
        CalendarEvent(
            title = composeTitle(kind, celebrantName),
            startAt = startedAt,
            endAt = startedAt.plusMinutes(DURATION_MINUTES),
            description = inviteUrl?.let { "초대 링크: $it" } ?: "",
        )

    private fun composeTitle(
        kind: CelebrationKind,
        celebrantName: String?,
    ): String {
        val title =
            if (celebrantName.isNullOrBlank()) {
                kind.partyLabel
            } else {
                "${celebrantName.trim()}님의 ${kind.partyLabel}"
            }
        return title.take(MAX_TITLE_LENGTH)
    }
}
```

- [ ] **Step 5: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.domain.policy.PartyCalendarEventPolicyTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/domain/vo/CelebrationKind.kt \
        src/main/kotlin/com/team2/server/calendar/domain/vo/CalendarEvent.kt \
        src/main/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicy.kt \
        src/test/kotlin/com/team2/server/calendar/domain/policy/PartyCalendarEventPolicyTest.kt
git commit -m "feat: 파티 캘린더 일정 조립 정책 추가"
```

---

### Task 3: 카카오 톡캘린더 어댑터

카카오 REST API를 호출하는 아웃바운드 어댑터. 이 레포의 첫 외부 HTTP 클라이언트다. 성공/실패 상태 코드를 도메인 에러로 바꾸는 책임까지 여기서 끝낸다.

카카오 계약: `POST /v2/api/calendar/create/event`, `POST /v2/api/calendar/update/event/host`. 둘 다 `Authorization: Bearer` + `application/x-www-form-urlencoded`, `event` 파라미터에 JSON 문자열. 생성 응답 본문은 `{"event_id":"..."}`.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/port/TalkCalendarPort.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarConfig.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapter.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapterTest.kt`

**Interfaces:**
- Consumes: `CalendarEvent` (Task 2), `BusinessException` / `ErrorCode` (common)
- Produces:
  - `interface TalkCalendarPort` — `fun createEvent(accessToken: String, event: CalendarEvent): String`, `fun updateEvent(accessToken: String, eventId: String, event: CalendarEvent): Boolean` (false = 카카오에 일정이 없음)
  - `KakaoTalkCalendarAdapter(restClient: RestClient, objectMapper: ObjectMapper, zoneId: ZoneId)`
  - 새 ErrorCode: `KAKAO_ACCESS_TOKEN_REQUIRED`, `KAKAO_TOKEN_INVALID`, `KAKAO_CALENDAR_CONSENT_REQUIRED`, `KAKAO_CALENDAR_UNAVAILABLE`, `TALK_CALENDAR_PARTY_ALREADY_STARTED`, `CALENDAR_REGISTRATION_IN_PROGRESS`
  - Bean 이름 `kakaoTalkCalendarRestClient`

- [ ] **Step 1: ErrorCode 6개를 추가한다**

`src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` 의 마지막 항목 `CANDLE_BLOW_NOT_STARTED(...)` 아래에 이어 붙인다.

```kotlin
    KAKAO_ACCESS_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "카카오 액세스 토큰이 필요합니다"),
    KAKAO_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "카카오 재로그인이 필요합니다"),
    KAKAO_CALENDAR_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "톡캘린더 사용 동의가 필요합니다"),
    KAKAO_CALENDAR_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 톡캘린더 연동에 실패했습니다"),
    TALK_CALENDAR_PARTY_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 파티는 캘린더에 등록할 수 없습니다"),
    CALENDAR_REGISTRATION_IN_PROGRESS(HttpStatus.CONFLICT, "캘린더 등록이 이미 진행 중입니다"),
```

- [ ] **Step 2: 실패하는 어댑터 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapterTest.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.http.HttpMethod as SpringHttpMethod

class KakaoTalkCalendarAdapterTest {
    private val builder = RestClient.builder().baseUrl("https://kapi.kakao.com")
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val adapter =
        KakaoTalkCalendarAdapter(
            restClient = builder.build(),
            objectMapper = ObjectMapper(),
            zoneId = ZoneId.of("Asia/Seoul"),
        )

    private val event =
        CalendarEvent(
            title = "지민님의 생일 파티",
            startAt = LocalDateTime.of(2026, 8, 20, 19, 0),
            endAt = LocalDateTime.of(2026, 8, 20, 19, 30),
            description = "초대 링크: https://example.com/invite/abc",
        )

    @Test
    fun `일정 생성 요청은 Bearer 토큰과 form 바디를 보내고 event_id 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(header("Authorization", "Bearer kakao-token"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("calendar_id=primary")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("20260820T100000Z")))
            .andRespond(withSuccess("""{"event_id":"event-1"}""", MediaType.APPLICATION_JSON))

        val eventId = adapter.createEvent("kakao-token", event)

        assertEquals("event-1", eventId)
        server.verify()
    }

    @Test
    fun `일정 수정 요청은 event_id 를 함께 보내고 true 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/update/event/host"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event_id=event-1")))
            .andRespond(withSuccess("""{"event_id":"event-1"}""", MediaType.APPLICATION_JSON))

        assertTrue(adapter.updateEvent("kakao-token", "event-1", event))
        server.verify()
    }

    @Test
    fun `수정 대상 일정이 없으면 false 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/update/event/host"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFalse(adapter.updateEvent("kakao-token", "event-1", event))
        server.verify()
    }

    @Test
    fun `401 이면 KAKAO_TOKEN_INVALID 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_TOKEN_INVALID, (exception as BusinessException).errorCode)
    }

    @Test
    fun `403 이면 KAKAO_CALENDAR_CONSENT_REQUIRED 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED, (exception as BusinessException).errorCode)
    }

    @Test
    fun `5xx 면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `생성 응답에 event_id 가 없으면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withSuccess("""{}""", MediaType.APPLICATION_JSON))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }
}
```

`20260820T100000Z` 는 KST 19:00 을 UTC 로 옮긴 값이다 (19:00 - 9시간 = 10:00 UTC).

- [ ] **Step 3: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.kakao.KakaoTalkCalendarAdapterTest"`
Expected: 컴파일 실패 — `KakaoTalkCalendarAdapter` unresolved reference

- [ ] **Step 4: Port 인터페이스를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/port/TalkCalendarPort.kt`

```kotlin
package com.team2.server.calendar.application.port

import com.team2.server.calendar.domain.vo.CalendarEvent

interface TalkCalendarPort {
    /** 일정을 새로 만들고 외부 일정 ID 를 반환한다. */
    fun createEvent(
        accessToken: String,
        event: CalendarEvent,
    ): String

    /**
     * 기존 일정을 갱신한다.
     *
     * @return 갱신 성공이면 true, 외부에 해당 일정이 없으면 false
     */
    fun updateEvent(
        accessToken: String,
        eventId: String,
        event: CalendarEvent,
    ): Boolean
}
```

- [ ] **Step 5: RestClient 설정을 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarConfig.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class KakaoTalkCalendarConfig(
    @Value("\${kakao.talk-calendar.base-url:https://kapi.kakao.com}")
    private val baseUrl: String,
) {
    /**
     * UseCase 트랜잭션 안에서 호출되므로 타임아웃을 짧게 잡아 DB 커넥션 점유 시간을 제한한다.
     */
    @Bean
    fun kakaoTalkCalendarRestClient(): RestClient {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(2))
                setReadTimeout(Duration.ofSeconds(5))
            }
        return RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
```

- [ ] **Step 6: 어댑터를 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapter.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val CREATE_EVENT_PATH = "/v2/api/calendar/create/event"
private const val UPDATE_EVENT_PATH = "/v2/api/calendar/update/event/host"
private const val DEFAULT_CALENDAR_ID = "primary"
private const val RECUR_UPDATE_TYPE_ALL = "ALL"
private val KAKAO_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

@Component
class KakaoTalkCalendarAdapter(
    @Qualifier("kakaoTalkCalendarRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value("\${app.time-zone:Asia/Seoul}") private val zoneId: ZoneId,
) : TalkCalendarPort {
    override fun createEvent(
        accessToken: String,
        event: CalendarEvent,
    ): String {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("calendar_id", DEFAULT_CALENDAR_ID)
                add("event", eventJson(event))
            }
        val response = post(CREATE_EVENT_PATH, accessToken, body)
        if (!response.statusCode.is2xxSuccessful) {
            throw toBusinessException(response.statusCode)
        }
        return readEventId(response.body)
    }

    override fun updateEvent(
        accessToken: String,
        eventId: String,
        event: CalendarEvent,
    ): Boolean {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("calendar_id", DEFAULT_CALENDAR_ID)
                add("event_id", eventId)
                add("recur_update_type", RECUR_UPDATE_TYPE_ALL)
                add("event", eventJson(event))
            }
        val response = post(UPDATE_EVENT_PATH, accessToken, body)
        if (response.statusCode == HttpStatus.NOT_FOUND) {
            return false
        }
        if (!response.statusCode.is2xxSuccessful) {
            throw toBusinessException(response.statusCode)
        }
        return true
    }

    private fun post(
        path: String,
        accessToken: String,
        body: MultiValueMap<String, String>,
    ): ResponseEntity<String> =
        try {
            restClient
                .post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                // 상태 코드를 직접 분기하려고 기본 예외 변환을 끈다.
                .onStatus({ it.isError }) { _, _ -> }
                .toEntity(String::class.java)
        } catch (e: RestClientException) {
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun eventJson(event: CalendarEvent): String =
        objectMapper.writeValueAsString(
            mapOf(
                "title" to event.title,
                "time" to
                    mapOf(
                        "start_at" to event.startAt.toKakaoUtc(),
                        "end_at" to event.endAt.toKakaoUtc(),
                        "time_zone" to zoneId.id,
                        "all_day" to false,
                    ),
                "description" to event.description,
            ),
        )

    private fun readEventId(body: String?): String {
        val parsed =
            runCatching { objectMapper.readValue(body ?: "", Map::class.java) }
                .getOrElse { throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE) }
        return parsed["event_id"] as? String
            ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
    }

    private fun toBusinessException(status: HttpStatusCode): BusinessException =
        when (status.value()) {
            HttpStatus.UNAUTHORIZED.value() -> BusinessException(ErrorCode.KAKAO_TOKEN_INVALID)
            HttpStatus.FORBIDDEN.value() -> BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
            else -> BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun LocalDateTime.toKakaoUtc(): String =
        atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).format(KAKAO_DATE_TIME)
}
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.kakao.KakaoTalkCalendarAdapterTest"`
Expected: PASS (7 tests)

`@Value` 로 `ZoneId` 를 주입할 때 컨버전 오류가 나면 생성자 파라미터를 `@Value("\${app.time-zone:Asia/Seoul}") zone: String` 으로 바꾸고 본문에서 `private val zoneId = ZoneId.of(zone)` 로 변환한다. 이 경우 테스트의 생성자 인자도 문자열로 맞춘다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt \
        src/main/kotlin/com/team2/server/calendar/application/port/TalkCalendarPort.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarConfig.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapter.kt \
        src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoTalkCalendarAdapterTest.kt
git commit -m "feat: 카카오 톡캘린더 연동 어댑터 추가"
```

---

### Task 4: 파티 조회 어댑터

calendar feature 가 party feature 에서 필요한 것만 가져오는 경계. chat feature 의 `PartyRealtimePartyEntryProfileAdapter` 와 같은 패턴이다.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyService.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/port/PartyCalendarInfoPort.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapter.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterTest.kt`

**Interfaces:**
- Consumes: `CelebrationKind` (Task 2), party 의 `PartyService`, `ParticipantService`, `PartyInviteService`
- Produces:
  - `PartyService.requireParty(partyId: Long): Party` — 없으면 `BusinessException(PARTY_NOT_FOUND)`
  - `data class PartyCalendarInfo(val partyId: Long, val celebrationKind: CelebrationKind, val celebrantName: String?, val startedAt: LocalDateTime, val inviteUrl: String?)`
  - `interface PartyCalendarInfoPort { fun loadForMember(partyId: Long, userId: Long, now: LocalDateTime): PartyCalendarInfo }`

- [ ] **Step 1: 실패하는 어댑터 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterTest.kt`

```kotlin
package com.team2.server.calendar.infrastructure.party

import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyPurpose
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartyCalendarInfoAdapterTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val partyInviteService: PartyInviteService = mock()
    private val adapter =
        PartyCalendarInfoAdapter(
            partyService = partyService,
            participantService = participantService,
            partyInviteService = partyInviteService,
            webBaseUrl = "https://example.com",
        )

    private val now = LocalDateTime.of(2026, 8, 18, 12, 0)
    private val startedAt = LocalDateTime.of(2026, 8, 20, 19, 0)

    @Test
    fun `호스트는 참여자 검증 없이 파티 정보를 얻는다`() {
        val party =
            PaperOnlyParty(
                ownerId = 10L,
                startedAt = startedAt,
                celebrantNickname = "지민",
                purpose = PartyPurpose.BIRTHDAY,
            )
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertEquals(CelebrationKind.BIRTHDAY, info.celebrationKind)
        assertEquals("지민", info.celebrantName)
        assertEquals(startedAt, info.startedAt)
        assertEquals("https://example.com/invite/token-1", info.inviteUrl)
    }

    @Test
    fun `참여 중인 멤버는 파티 정보를 얻는다`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt, celebrantNickname = "지민")
        val participant = Participant(party = party, hasLeft = false)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, 20L, null)).thenReturn(participant)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 20L, now = now)

        assertEquals(startedAt, info.startedAt)
    }

    @Test
    fun `파티를 나간 참여자는 PARTY_FORBIDDEN`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt)
        val participant = Participant(party = party, hasLeft = true)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, 20L, null)).thenReturn(participant)

        val exception =
            kotlin.runCatching { adapter.loadForMember(partyId = 1L, userId = 20L, now = now) }.exceptionOrNull()

        assertEquals(ErrorCode.PARTY_FORBIDDEN, (exception as BusinessException).errorCode)
    }

    @Test
    fun `사용 가능한 초대 링크가 없으면 inviteUrl 은 null`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt, celebrantNickname = "지민")
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any()))
            .thenThrow(BusinessException(ErrorCode.PARTY_INVITE_NOT_FOUND))

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertNull(info.inviteUrl)
    }

    @Test
    fun `파티 목적을 CelebrationKind 로 매핑한다`() {
        val party =
            PaperOnlyParty(ownerId = 10L, startedAt = startedAt, purpose = PartyPurpose.WEDDING)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertEquals(CelebrationKind.WEDDING, info.celebrationKind)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.party.PartyCalendarInfoAdapterTest"`
Expected: 컴파일 실패 — `PartyCalendarInfoAdapter`, `partyService.requireParty` unresolved reference

- [ ] **Step 3: PartyService 에 requireParty 를 추가한다**

`src/main/kotlin/com/team2/server/party/application/service/PartyService.kt` 의 `requireRealtimeParty` 아래에 추가한다. 기존 private `findParty` 를 그대로 쓴다.

```kotlin
    /** 파티 종류와 무관하게 파티를 조회한다. 없으면 PARTY_NOT_FOUND. */
    fun requireParty(partyId: Long): Party = findParty(partyId)
```

- [ ] **Step 4: Port 와 결과 모델을 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/port/PartyCalendarInfoPort.kt`

```kotlin
package com.team2.server.calendar.application.port

import com.team2.server.calendar.domain.vo.CelebrationKind
import java.time.LocalDateTime

interface PartyCalendarInfoPort {
    /**
     * 캘린더 등록에 필요한 파티 정보를 읽는다.
     * 요청자가 파티의 호스트도 현재 참여자도 아니면 PARTY_FORBIDDEN 을 던진다.
     */
    fun loadForMember(
        partyId: Long,
        userId: Long,
        now: LocalDateTime,
    ): PartyCalendarInfo
}

data class PartyCalendarInfo(
    val partyId: Long,
    val celebrationKind: CelebrationKind,
    val celebrantName: String?,
    val startedAt: LocalDateTime,
    val inviteUrl: String?,
)
```

- [ ] **Step 5: 어댑터를 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapter.kt`

```kotlin
package com.team2.server.calendar.infrastructure.party

import com.team2.server.calendar.application.port.PartyCalendarInfo
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyPurpose
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PartyCalendarInfoAdapter(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val partyInviteService: PartyInviteService,
    @Value("\${app.web-base-url}") private val webBaseUrl: String,
) : PartyCalendarInfoPort {
    override fun loadForMember(
        partyId: Long,
        userId: Long,
        now: LocalDateTime,
    ): PartyCalendarInfo {
        val party = partyService.requireParty(partyId)
        requireMember(party, partyId, userId)

        return PartyCalendarInfo(
            partyId = partyId,
            celebrationKind = party.purpose.toCelebrationKind(),
            celebrantName = party.celebrantNickname ?: party.name,
            startedAt = party.startedAt,
            inviteUrl = findInviteUrl(partyId, now),
        )
    }

    private fun requireMember(
        party: Party,
        partyId: Long,
        userId: Long,
    ) {
        if (party.ownerId == userId) return
        val participant = participantService.requireCallerParticipant(partyId, userId, null)
        if (participant.hasLeft) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }

    /**
     * 사용 가능한 초대 링크가 없어도 캘린더 등록 자체는 성공시킨다.
     * PartyInviteService 에는 nullable 조회가 없어 예외를 흡수한다.
     */
    private fun findInviteUrl(
        partyId: Long,
        now: LocalDateTime,
    ): String? =
        runCatching { partyInviteService.findLatestUsableInviteToken(partyId, now) }
            .getOrNull()
            ?.let { "$webBaseUrl/invite/$it" }

    private fun PartyPurpose.toCelebrationKind(): CelebrationKind =
        when (this) {
            PartyPurpose.BIRTHDAY -> CelebrationKind.BIRTHDAY
            PartyPurpose.JOB_CHANGE -> CelebrationKind.JOB_CHANGE
            PartyPurpose.WEDDING -> CelebrationKind.WEDDING
        }
}
```

- [ ] **Step 6: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.party.PartyCalendarInfoAdapterTest"`
Expected: PASS (5 tests)

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/application/service/PartyService.kt \
        src/main/kotlin/com/team2/server/calendar/application/port/PartyCalendarInfoPort.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapter.kt \
        src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterTest.kt
git commit -m "feat: 캘린더용 파티 정보 조회 어댑터 추가"
```

---

### Task 5: 등록 Service 와 UseCase

트랜잭션 경계와 등록/갱신 분기. 이 태스크가 끝나면 HTTP 껍데기만 남는다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventCommand.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventResult.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCaseTest.kt`

**Interfaces:**
- Consumes: `CalendarRegistrationRepository` (Task 1), `PartyCalendarEventPolicy` (Task 2), `TalkCalendarPort` (Task 3), `PartyCalendarInfoPort` (Task 4)
- Produces:
  - `CalendarRegistrationService` — `fun find(userId: Long, partyId: Long): CalendarRegistration?`, `fun reserve(userId: Long, partyId: Long): CalendarRegistration`, `fun linkEvent(registration: CalendarRegistration, eventId: String)`
  - `data class RegisterPartyTalkCalendarEventCommand(val partyId: Long, val userId: Long, val kakaoAccessToken: String)`
  - `data class RegisterPartyTalkCalendarEventResult(val eventId: String, val updated: Boolean)`
  - `RegisterPartyTalkCalendarEventUseCase` — `operator fun invoke(command): RegisterPartyTalkCalendarEventResult`

- [ ] **Step 1: 실패하는 UseCase 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCaseTest.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.port.PartyCalendarInfo
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterPartyTalkCalendarEventUseCaseTest {
    private val partyCalendarInfoPort: PartyCalendarInfoPort = mock()
    private val talkCalendarPort: TalkCalendarPort = mock()
    private val calendarRegistrationService: CalendarRegistrationService = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 18, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        RegisterPartyTalkCalendarEventUseCase(
            partyCalendarInfoPort,
            talkCalendarPort,
            calendarRegistrationService,
            clock,
        )

    private val command =
        RegisterPartyTalkCalendarEventCommand(partyId = 1L, userId = 10L, kakaoAccessToken = "kakao-token")

    private fun partyInfo(startedAt: LocalDateTime = LocalDateTime.of(2026, 8, 20, 19, 0)) =
        PartyCalendarInfo(
            partyId = 1L,
            celebrationKind = CelebrationKind.BIRTHDAY,
            celebrantName = "지민",
            startedAt = startedAt,
            inviteUrl = "https://example.com/invite/abc",
        )

    private fun registration(eventId: String?) =
        CalendarRegistration(
            userId = 10L,
            partyId = 1L,
            provider = CalendarProvider.KAKAO_TALK,
            eventId = eventId,
        )

    @Test
    fun `이력이 없으면 행을 먼저 확보하고 일정을 생성한다`() {
        val reserved = registration(null)
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(reserved)
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        val result = useCase(command)

        assertEquals("event-1", result.eventId)
        assertFalse(result.updated)
        verify(calendarRegistrationService).linkEvent(reserved, "event-1")
    }

    @Test
    fun `생성되는 일정은 정책이 만든 제목과 30분 길이를 갖는다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(registration(null))
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        useCase(command)

        val captor = argumentCaptor<CalendarEvent>()
        verify(talkCalendarPort).createEvent(eq("kakao-token"), captor.capture())
        assertEquals("지민님의 생일 파티", captor.firstValue.title)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), captor.firstValue.startAt)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 30), captor.firstValue.endAt)
        assertEquals("초대 링크: https://example.com/invite/abc", captor.firstValue.description)
    }

    @Test
    fun `이력이 있으면 기존 일정을 갱신한다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(registration("event-1"))
        whenever(talkCalendarPort.updateEvent(eq("kakao-token"), eq("event-1"), any())).thenReturn(true)

        val result = useCase(command)

        assertEquals("event-1", result.eventId)
        assertTrue(result.updated)
        verify(talkCalendarPort, never()).createEvent(any(), any())
        verify(calendarRegistrationService, never()).reserve(any(), any())
    }

    @Test
    fun `카카오에 일정이 없으면 같은 이력으로 다시 생성한다`() {
        val existing = registration("event-1")
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(existing)
        whenever(talkCalendarPort.updateEvent(eq("kakao-token"), eq("event-1"), any())).thenReturn(false)
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-2")

        val result = useCase(command)

        assertEquals("event-2", result.eventId)
        assertFalse(result.updated)
        verify(calendarRegistrationService).linkEvent(existing, "event-2")
        verify(calendarRegistrationService, never()).reserve(any(), any())
    }

    @Test
    fun `이미 시작된 파티는 등록할 수 없다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow))
            .thenReturn(partyInfo(startedAt = fixedNow))

        val exception = kotlin.runCatching { useCase(command) }.exceptionOrNull()

        assertEquals(
            ErrorCode.TALK_CALENDAR_PARTY_ALREADY_STARTED,
            (exception as BusinessException).errorCode,
        )
        verify(talkCalendarPort, never()).createEvent(any(), any())
    }

    @Test
    fun `시작 1분 전이면 등록할 수 있다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow))
            .thenReturn(partyInfo(startedAt = fixedNow.plusMinutes(1)))
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(registration(null))
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        assertEquals("event-1", useCase(command).eventId)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCaseTest"`
Expected: 컴파일 실패 — `RegisterPartyTalkCalendarEventUseCase` 외 unresolved reference

- [ ] **Step 3: Service 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt`

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class CalendarRegistrationService(
    private val calendarRegistrationRepository: CalendarRegistrationRepository,
) {
    fun find(
        userId: Long,
        partyId: Long,
    ): CalendarRegistration? =
        calendarRegistrationRepository.findByUserIdAndPartyIdAndProvider(
            userId = userId,
            partyId = partyId,
            provider = CalendarProvider.KAKAO_TALK,
        )

    /**
     * 카카오 호출 전에 등록 자리를 먼저 잡는다.
     * 동시에 들어온 두 번째 요청은 UNIQUE 제약에 걸려 여기서 막힌다.
     */
    fun reserve(
        userId: Long,
        partyId: Long,
    ): CalendarRegistration =
        try {
            calendarRegistrationRepository.saveAndFlush(
                CalendarRegistration(
                    userId = userId,
                    partyId = partyId,
                    provider = CalendarProvider.KAKAO_TALK,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (e.isConstraintViolation(CalendarRegistration.UK_USER_PARTY_PROVIDER)) {
                throw BusinessException(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS)
            }
            throw e
        }

    fun linkEvent(
        registration: CalendarRegistration,
        eventId: String,
    ) {
        registration.linkEvent(eventId)
        calendarRegistrationRepository.save(registration)
    }
}
```

- [ ] **Step 4: Command / Result 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventCommand.kt`

```kotlin
package com.team2.server.calendar.application.dto

data class RegisterPartyTalkCalendarEventCommand(
    val partyId: Long,
    val userId: Long,
    val kakaoAccessToken: String,
)
```

`src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventResult.kt`

```kotlin
package com.team2.server.calendar.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "톡캘린더 일정 등록 결과")
data class RegisterPartyTalkCalendarEventResult(
    @Schema(description = "카카오 톡캘린더 일정 ID", example = "63630868d89d8b4150bbb712")
    val eventId: String,
    @Schema(description = "기존 일정을 갱신했으면 true, 새로 만들었으면 false", example = "false")
    val updated: Boolean,
)
```

- [ ] **Step 5: UseCase 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCase.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.policy.PartyCalendarEventPolicy
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RegisterPartyTalkCalendarEventUseCase(
    private val partyCalendarInfoPort: PartyCalendarInfoPort,
    private val talkCalendarPort: TalkCalendarPort,
    private val calendarRegistrationService: CalendarRegistrationService,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(command: RegisterPartyTalkCalendarEventCommand): RegisterPartyTalkCalendarEventResult {
        val now = LocalDateTime.now(clock)
        val info = partyCalendarInfoPort.loadForMember(command.partyId, command.userId, now)
        if (!now.isBefore(info.startedAt)) {
            throw BusinessException(ErrorCode.TALK_CALENDAR_PARTY_ALREADY_STARTED)
        }

        val event =
            PartyCalendarEventPolicy.compose(
                kind = info.celebrationKind,
                celebrantName = info.celebrantName,
                startedAt = info.startedAt,
                inviteUrl = info.inviteUrl,
            )
        val existing = calendarRegistrationService.find(command.userId, command.partyId)
        val existingEventId = existing?.eventId

        if (existing != null && existingEventId != null &&
            talkCalendarPort.updateEvent(command.kakaoAccessToken, existingEventId, event)
        ) {
            return RegisterPartyTalkCalendarEventResult(eventId = existingEventId, updated = true)
        }

        // 이력이 없거나, 사용자가 카카오에서 일정을 지워 갱신할 대상이 사라진 경우
        val registration = existing ?: calendarRegistrationService.reserve(command.userId, command.partyId)
        return RegisterPartyTalkCalendarEventResult(
            eventId = createEvent(command.kakaoAccessToken, event, registration),
            updated = false,
        )
    }

    private fun createEvent(
        accessToken: String,
        event: CalendarEvent,
        registration: CalendarRegistration,
    ): String {
        val eventId = talkCalendarPort.createEvent(accessToken, event)
        calendarRegistrationService.linkEvent(registration, eventId)
        return eventId
    }
}
```

- [ ] **Step 6: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCaseTest"`
Expected: PASS (6 tests)

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt \
        src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventCommand.kt \
        src/main/kotlin/com/team2/server/calendar/application/dto/RegisterPartyTalkCalendarEventResult.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCase.kt \
        src/test/kotlin/com/team2/server/calendar/application/usecase/RegisterPartyTalkCalendarEventUseCaseTest.kt
git commit -m "feat: 톡캘린더 일정 등록 유스케이스 추가"
```

---

### Task 6: 엔드포인트와 설정

HTTP 껍데기, Swagger 스펙, 환경 설정, 그리고 카카오를 호출하지 않고 끝나는 경로에 대한 통합 테스트.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Test: `src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt`

**Interfaces:**
- Consumes: `RegisterPartyTalkCalendarEventUseCase`, `RegisterPartyTalkCalendarEventCommand`, `RegisterPartyTalkCalendarEventResult` (Task 5)
- Produces: `POST /api/v1/parties/{partyId}/talk-calendar`, 헤더 `X-Kakao-Access-Token`
- 설정 키: `app.web-base-url`, `kakao.talk-calendar.base-url`

- [ ] **Step 1: 실패하는 컨트롤러 통합 테스트 작성**

카카오를 실제로 호출하는 성공 경로는 여기서 다루지 않는다. `@MockitoBean` 은 Spring 컨텍스트 캐시를 분리시켜 금지돼 있고(`docs/testing-rules.md`), 성공 경로는 Task 5 의 UseCase 테스트가 이미 덮는다. 여기서는 카카오 호출 이전에 끝나는 경로만 검증한다.

`src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
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
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TalkCalendarControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `인증 없이 요청하면 401`() {
            mockMvc
                .post("/api/v1/parties/1/talk-calendar") {
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `카카오 액세스 토큰 헤더가 없으면 400`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `존재하지 않는 파티면 404`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/999999/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `이미 시작된 파티면 409`() {
            val fixture = saveHostAndParty(LocalDateTime.now().minusHours(1))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("TALK_CALENDAR_PARTY_ALREADY_STARTED") }
                }
        }

        @Test
        fun `파티 멤버가 아니면 403`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            val stranger =
                userRepository.save(
                    User(
                        name = "남",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "calendar-stranger",
                        email = "calendar-stranger@test.local",
                    ),
                )

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${tokenProvider.issue(stranger)}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isForbidden() }
                }
        }

        private data class HostFixture(
            val partyId: Long,
            val hostToken: String,
        )

        private fun saveHostAndParty(startedAt: LocalDateTime): HostFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "calendar-host",
                        email = "calendar-host@test.local",
                    ),
                )
            val party =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = host.id,
                        startedAt = startedAt,
                        celebrantNickname = "지민",
                    ),
                )
            return HostFixture(partyId = party.id, hostToken = tokenProvider.issue(host))
        }
    }
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.TalkCalendarControllerTest"`
Expected: 404 (핸들러 없음) 또는 컨텍스트 로딩 실패 — `app.web-base-url` 미설정

- [ ] **Step 3: Swagger 스펙 인터페이스를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Talk Calendar", description = "카카오 톡캘린더 연동 API")
interface TalkCalendarApi {
    @Operation(
        summary = "파티 일정을 카카오 톡캘린더에 등록",
        description = """
파티 시작 전까지만 등록할 수 있고, 파티의 호스트 또는 현재 참여자만 호출할 수 있다.
이미 등록한 파티를 다시 호출하면 기존 일정을 갱신한다.

**카카오 액세스 토큰**
클라이언트가 카카오 SDK 로 톡캘린더 동의를 받은 뒤 얻은 액세스 토큰을 `X-Kakao-Access-Token` 헤더로 전달한다.
서버는 이 토큰을 저장하지 않는다.

**에러 코드**
- `KAKAO_ACCESS_TOKEN_REQUIRED` (400): 헤더 누락
- `KAKAO_TOKEN_INVALID` (401): 카카오 재로그인 필요
- `KAKAO_CALENDAR_CONSENT_REQUIRED` (403): 톡캘린더 추가 동의 필요
- `TALK_CALENDAR_PARTY_ALREADY_STARTED` (409): 이미 시작된 파티
- `CALENDAR_REGISTRATION_IN_PROGRESS` (409): 동시 요청 충돌
- `KAKAO_CALENDAR_UNAVAILABLE` (502): 카카오 장애 또는 타임아웃
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "등록 또는 갱신 성공")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun registerPartyEvent(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
        @Parameter(
            description = "카카오 액세스 토큰",
            `in` = ParameterIn.HEADER,
            name = "X-Kakao-Access-Token",
            required = true,
        )
        kakaoAccessToken: String?,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult>
}
```

- [ ] **Step 4: 컨트롤러를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class TalkCalendarController(
    private val registerPartyTalkCalendarEventUseCase: RegisterPartyTalkCalendarEventUseCase,
) : TalkCalendarApi {
    @PostMapping("/{partyId}/talk-calendar")
    override fun registerPartyEvent(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
        @RequestHeader(value = "X-Kakao-Access-Token", required = false) kakaoAccessToken: String?,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult> {
        if (kakaoAccessToken.isNullOrBlank()) {
            throw BusinessException(ErrorCode.KAKAO_ACCESS_TOKEN_REQUIRED)
        }
        return ApiResponse.success(
            HttpStatus.OK,
            registerPartyTalkCalendarEventUseCase(
                RegisterPartyTalkCalendarEventCommand(
                    partyId = partyId,
                    userId = principal.userId,
                    kakaoAccessToken = kakaoAccessToken,
                ),
            ),
        )
    }
}
```

`required = false` 로 받고 직접 검사하는 이유는 헤더 누락을 `KAKAO_ACCESS_TOKEN_REQUIRED` 로 내려주기 위해서다. `required = true` 면 Spring 이 먼저 일반 400 을 던진다.

- [ ] **Step 5: 운영 설정을 추가한다**

`src/main/resources/application.yml` 의 `support:` 블록 위에 추가한다.

```yaml
# 배포 전 환경별 yml(application-prod.yml 등)에서 실제 운영 URL로 반드시 override
app:
  web-base-url: "https://localhost:3000"

kakao:
  talk-calendar:
    base-url: "https://kapi.kakao.com"
```

- [ ] **Step 6: 테스트 설정을 추가한다**

`src/test/resources/application.yml` 의 기존 `app:` 블록 안에 `web-base-url` 을 넣고, 최상위에 `kakao:` 를 추가한다.

```yaml
app:
  web-base-url: "https://test.example.com"
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LXdpdGgtYXQtbGVhc3QtMjU2LWJpdHMtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobS0xMjM0NTY3OA==
    expiration-hours: 24
  oauth2:
    authorized-redirect-uris: http://localhost:3000/oauth/redirect

kakao:
  talk-calendar:
    base-url: "https://kapi.kakao.com"
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.TalkCalendarControllerTest"`
Expected: PASS (5 tests)

- [ ] **Step 8: 전체 빌드로 회귀를 확인한다**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — ktlint 포맷 오류가 나면 지적된 파일을 고친다. ArchUnit 테스트도 함께 통과해야 한다.

- [ ] **Step 9: 컨테이너 누수를 확인한다**

Run: `docker ps -a --filter "label=org.testcontainers"`
Expected: 잔존 컨테이너 0개

- [ ] **Step 10: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt \
        src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt \
        src/main/resources/application.yml \
        src/test/resources/application.yml \
        src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt
git commit -m "feat: 카카오 톡캘린더 일정 등록 API 추가"
```

---

## 남은 확인 사항

구현 중 또는 직후에 사람이 확인해야 하는 것들이다. 코드로 해결되지 않는다.

- 카카오 개발자 콘솔에서 톡캘린더 동의항목의 영문 키를 확인하고 앱에 활성화한다 (통상 `talk_calendar_task`). 클라이언트가 이 scope 로 추가 동의를 받아야 한다.
- 카카오가 "동의 없음"을 401 로 주는지 403 으로 주는지 실제 응답으로 확인하고, 다르면 `KakaoTalkCalendarAdapter.toBusinessException` 의 분기를 고친다.
- `app.web-base-url` 을 `application-dev.yml` / `application-prod.yml` 에 환경별 실제 값으로 넣는다.
- 초대 링크 경로 `/invite/{token}` 이 프론트 라우팅과 맞는지 확인한다. 다르면 `PartyCalendarInfoAdapter.findInviteUrl` 을 고친다.
