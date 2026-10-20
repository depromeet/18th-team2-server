# 비회원 톡캘린더 일정 등록 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비회원이 우리 서비스 가입 없이 톡캘린더 동의 한 번으로 파티 일정을 자기 카카오 캘린더에 등록한다.

**Architecture:** 동의 티켓에 주체 종류(회원/비회원)를 실어 콜백에서 분기한다. 비회원이면 콜백이 인가 코드를 토큰으로 교환해 그 자리에서 일정을 만들고 토큰을 폐기한다. 식별자는 카카오 회원번호이고, 권한 근거는 초대 토큰이다. 회원 경로는 변경하지 않는다.

**Tech Stack:** Kotlin, Spring Boot 4, JPA/Hibernate, Flyway, MySQL 8, JUnit5 + kotlin.test, MockRestServiceServer, Testcontainers

**Spec:** `docs/superpowers/specs/2026-10-14-guest-talk-calendar-design.md`

## Global Constraints

- 레이어드 아키텍처 규칙 준수 — `.claude/rules/layered-architecture.md`. UseCase는 1 public 메서드, `@Transactional`은 UseCase에만, 60줄 이내, 생성자 의존성 5개 이내. Service는 `@Transactional` 금지, 의존성 4개 이내.
- UseCase는 `application/dto` 출력 모델을 반환하고 도메인 객체를 노출하지 않는다.
- `@SpringBootTest` / `@DataJpaTest`는 반드시 `TestcontainersConfiguration` 경유 (`@Import` 또는 base class 상속) — `docs/testing-rules.md`.
- 새 통합 테스트는 기존 컨텍스트 지문 4개 중 하나에 정확히 맞춘다. `@MockBean` / `@TestPropertySource` / `@ActiveProfiles` 금지 (컨텍스트 캐시 분리).
- 커밋 메시지: `<type>: <한국어 설명>`, 50자 이내, 마침표 없음, scope 없음, 한국어 명사형 종결. `git add` 는 파일 개별 지정 (`-A` / `.` 금지).
- `--no-verify` 금지. `develop` 직접 커밋 금지.
- 회원 플로우(`SaveKakaoCalendarConsentUseCase`, `kakao_calendar_connection`)는 변경하지 않는다.
- 카카오 회원번호 컬럼 길이는 100 — `users.provider_id`와 동일.
- 작업 브랜치: `feature/guest-talk-calendar-registration` (이미 생성됨, `origin/develop` 기준)

---

## File Structure

**신규**

| 파일 | 책임 |
|---|---|
| `calendar/application/service/ConsentSubject.kt` | 티켓 주체 sealed 타입 (Member / Guest) |
| `calendar/application/usecase/ExchangeKakaoConsentUseCase.kt` | 인가 코드 → (액세스 토큰, 회원번호) |
| `calendar/application/usecase/RegisterGuestTalkCalendarEventUseCase.kt` | 초대 토큰·회원번호·토큰 → 일정 등록 |
| `calendar/application/dto/GuestCalendarRegistrationResult.kt` | 비회원 등록 UseCase 출력 모델 |
| `calendar/api/GuestTalkCalendarConsentController.kt` | 비회원 동의 진입 URL 발급 |
| `calendar/api/GuestTalkCalendarConsentApi.kt` | 위 컨트롤러의 Swagger 문서 |
| `resources/db/migration/V18__add_calendar_registration_guest.sql` | 스키마 변경 |

**수정**

| 파일 | 변경 |
|---|---|
| `calendar/application/service/ConsentTicketSigner.kt` | 주체 종류 접두사 발급·검증 |
| `calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt` | `ConsentOutcome` 에 4값 추가 |
| `calendar/api/KakaoCalendarConsentController.kt` | 콜백에서 주체별 분기 |
| `calendar/domain/entity/CalendarRegistration.kt` | `userId` nullable, `kakaoProviderId` 추가 |
| `calendar/application/service/CalendarRegistrationService.kt` | 비회원 조회·예약 메서드 |
| `calendar/infrastructure/persistence/CalendarRegistrationRepository.kt` | 비회원 조회 메서드 |
| `calendar/application/port/PartyCalendarInfoPort.kt` | `loadForInviteToken` 추가 |
| `calendar/infrastructure/party/PartyCalendarInfoAdapter.kt` | 위 메서드 구현 |
| `auth/config/SecurityConfig.kt` | 신규 엔드포인트 permitAll |
| `test/.../db/FlywayMigrationTest.kt` | V18 검증 |

작업 순서는 의존 방향을 따른다: 티켓 → 스키마/엔티티 → 포트 → UseCase → 컨트롤러 → 통합.

---

## Task 1: 티켓에 주체 종류를 싣는다

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/service/ConsentSubject.kt`
- Modify: `src/main/kotlin/com/team2/server/calendar/application/service/ConsentTicketSigner.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt`

**Interfaces:**
- Consumes: `JwtProperties.secret`, `java.time.Clock` (기존 생성자 그대로)
- Produces:
  - `sealed interface ConsentSubject`
  - `data class ConsentSubject.Member(val userId: Long) : ConsentSubject`
  - `data class ConsentSubject.Guest(val inviteToken: String) : ConsentSubject`
  - `ConsentTicketSigner.issue(subject: ConsentSubject): String`
  - `ConsentTicketSigner.verify(ticket: String): ConsentSubject?`
  - `ConsentTicketSigner.TTL_SECONDS: Long` (기존 300L 유지)

기존 `issue(userId: Long)` 와 `verify(ticket): Long?` 는 제거한다. 호출처는 Task 7에서 고친다. 이 Task의 테스트는 새 시그니처만 쓴다.

- [ ] **Step 1: 기존 테스트 파일 존재 여부 확인**

Run: `ls src/test/kotlin/com/team2/server/calendar/application/service/`

기존 `ConsentTicketSignerTest.kt` 가 있으면 그 파일에 이어 쓰고, 없으면 새로 만든다. 기존 테스트가 구 시그니처(`issue(53L)`)를 쓰고 있으면 이 Task에서 새 시그니처로 함께 고친다 — 그러지 않으면 컴파일이 깨져 RED를 볼 수 없다.

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt` 에 아래를 둔다. 기존 파일이 있으면 클래스 본문에 테스트만 추가하고 셋업은 기존 것을 쓴다.

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.auth.config.JwtProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConsentTicketSignerTest {
    private val fixedInstant: Instant = Instant.parse("2026-10-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val properties =
        JwtProperties(
            secret = "test-secret-key-for-consent-ticket-signer-0123456789",
            accessTokenValidityInSeconds = 3600,
            refreshTokenValidityInSeconds = 3600,
        )
    private val signer = ConsentTicketSigner(properties, clock)

    @Test
    fun `회원 티켓은 사용자 번호로 복원된다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))

        assertEquals(ConsentSubject.Member(53L), signer.verify(ticket))
    }

    @Test
    fun `비회원 티켓은 초대 토큰으로 복원된다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))

        assertEquals(ConsentSubject.Guest("a796ba5f"), signer.verify(ticket))
    }

    @Test
    fun `회원 티켓은 비회원 주체로 읽히지 않는다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))

        assertIs<ConsentSubject.Member>(signer.verify(ticket))
    }

    @Test
    fun `비회원 티켓은 회원 주체로 읽히지 않는다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))

        assertIs<ConsentSubject.Guest>(signer.verify(ticket))
    }

    @Test
    fun `서명이 변조된 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))
        val tampered = ticket.substringBefore('.') + ".AAAA"

        assertNull(signer.verify(tampered))
    }

    @Test
    fun `payload 가 변조된 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))
        val forged = signer.issue(ConsentSubject.Member(99L))
        val mixed = forged.substringBefore('.') + "." + ticket.substringAfter('.')

        assertNull(signer.verify(mixed))
    }

    @Test
    fun `수명이 지난 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))
        val later =
            ConsentTicketSigner(
                properties,
                Clock.fixed(fixedInstant.plusSeconds(ConsentTicketSigner.TTL_SECONDS + 1), ZoneOffset.UTC),
            )

        assertNull(later.verify(ticket))
    }

    @Test
    fun `형식이 깨진 티켓은 거부한다`() {
        assertNull(signer.verify(""))
        assertNull(signer.verify("no-dot"))
        assertNull(signer.verify(".onlysig"))
    }
}
```

`JwtProperties` 생성자 파라미터 이름이 위와 다르면 실제 정의를 읽어 맞춘다. Run: `cat src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt`

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.ConsentTicketSignerTest"`
Expected: 컴파일 실패 — `ConsentSubject` 미정의, `issue(ConsentSubject)` 없음

- [ ] **Step 4: `ConsentSubject` 를 만든다**

`src/main/kotlin/com/team2/server/calendar/application/service/ConsentSubject.kt`:

```kotlin
package com.team2.server.calendar.application.service

/**
 * 동의 플로우를 시작한 주체.
 *
 * 회원은 사용자 번호로, 비회원은 초대 토큰으로 식별한다. 비회원은 서비스 사용자 행이 없어
 * 사용자 번호를 실을 수 없고, 콜백에서 어느 파티에 등록할지 알아야 하므로 초대 토큰을 나른다.
 */
sealed interface ConsentSubject {
    data class Member(val userId: Long) : ConsentSubject

    data class Guest(val inviteToken: String) : ConsentSubject
}
```

- [ ] **Step 5: `ConsentTicketSigner` 를 고친다**

`issue` 와 `verify` 를 아래로 교체한다. `sign` / `encode` / `key` / `TTL_SECONDS` 는 그대로 둔다.

```kotlin
    fun issue(subject: ConsentSubject): String {
        val payload = "${tag(subject)}:${identifier(subject)}:${Instant.now(clock).epochSecond}"
        val encodedPayload = encode(payload.toByteArray(StandardCharsets.UTF_8))
        return "$encodedPayload.${encode(sign(encodedPayload))}"
    }

    @Suppress("ReturnCount")
    fun verify(ticket: String): ConsentSubject? {
        val encodedPayload = ticket.substringBefore('.', missingDelimiterValue = "")
        val encodedSignature = ticket.substringAfter('.', missingDelimiterValue = "")
        if (encodedPayload.isEmpty() || encodedSignature.isEmpty()) return null

        val expected = encode(sign(encodedPayload))
        if (!MessageDigest.isEqual(expected.toByteArray(), encodedSignature.toByteArray())) return null

        val payload =
            runCatching { String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8) }
                .getOrNull() ?: return null
        val parts = payload.split(':')
        if (parts.size != PAYLOAD_PART_COUNT) return null

        val issuedAtEpochSecond = parts[2].toLongOrNull() ?: return null
        val elapsed = Instant.now(clock).epochSecond - issuedAtEpochSecond
        if (elapsed < 0 || elapsed > TTL_SECONDS) return null

        return subjectOf(parts[0], parts[1])
    }

    private fun tag(subject: ConsentSubject): String =
        when (subject) {
            is ConsentSubject.Member -> MEMBER_TAG
            is ConsentSubject.Guest -> GUEST_TAG
        }

    private fun identifier(subject: ConsentSubject): String =
        when (subject) {
            is ConsentSubject.Member -> subject.userId.toString()
            is ConsentSubject.Guest -> subject.inviteToken
        }

    /** 접두사를 모르면 종류를 추측하지 않고 버린다. */
    private fun subjectOf(
        tag: String,
        identifier: String,
    ): ConsentSubject? =
        when (tag) {
            MEMBER_TAG -> identifier.toLongOrNull()?.let { ConsentSubject.Member(it) }
            GUEST_TAG -> identifier.takeIf { it.isNotBlank() }?.let { ConsentSubject.Guest(it) }
            else -> null
        }
```

companion object 에 상수를 더한다:

```kotlin
    companion object {
        const val TTL_SECONDS = 300L
        private const val MEMBER_TAG = "u"
        private const val GUEST_TAG = "g"
        private const val PAYLOAD_PART_COUNT = 3
    }
```

클래스 KDoc 의 티켓 형식 설명을 함께 고친다 — `base64url(userId:발급시각)` 을 `base64url(주체종류:식별자:발급시각)` 으로.

- [ ] **Step 6: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.ConsentTicketSignerTest"`
Expected: PASS

컴파일이 깨진 호출처(`KakaoCalendarConsentController`, `KakaoCalendarConnectionController` 등)가 있으면 이 Task에서 **새 시그니처로 최소 수정만** 한다 — 회원은 `issue(ConsentSubject.Member(userId))`, 콜백은 `verify(...)` 결과를 `is ConsentSubject.Member` 로 좁혀 기존 로직에 넘긴다. 동작 변경은 Task 7에서 한다.

Run: `./gradlew compileKotlin compileTestKotlin` 으로 전체 컴파일을 확인한다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/service/ConsentSubject.kt \
        src/main/kotlin/com/team2/server/calendar/application/service/ConsentTicketSigner.kt \
        src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt
# 컴파일 때문에 함께 고친 호출처가 있으면 그 파일도 개별 지정해 추가한다
git commit -m "feat: 동의 티켓에 주체 종류 추가"
```

---

## Task 2: 스키마와 엔티티를 비회원 등록에 맞춘다

**Files:**
- Create: `src/main/resources/db/migration/V18__add_calendar_registration_guest.sql`
- Modify: `src/main/kotlin/com/team2/server/calendar/domain/entity/CalendarRegistration.kt`
- Test: `src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt`

**Interfaces:**
- Produces:
  - `CalendarRegistration(userId: Long?, partyId: Long, provider: CalendarProvider, kakaoProviderId: String? = null, eventId: String? = null)`
  - `CalendarRegistration.UK_USER_PARTY_PROVIDER` (기존 상수 유지)
  - `CalendarRegistration.UK_KAKAO_PARTY_PROVIDER = "uk_calendar_registration_kakao_party_provider"`

`userId` 가 `Long?` 로 바뀌므로 기존 사용처(`CalendarRegistrationService`, `RegisterPartyTalkCalendarEventUseCase`)의 컴파일을 확인한다. 두 곳 모두 `userId` 를 쓰기만 하고 읽지 않으므로 수정이 없을 것으로 예상되지만, 깨지면 이 Task에서 고친다.

- [ ] **Step 1: 기존 마이그레이션 테스트 패턴을 읽는다**

Run: `grep -n "V17\|notice_seen" src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt`

가장 최근 마이그레이션(V17)의 검증 방식을 그대로 따른다. 컬럼 존재·nullability·인덱스를 raw JDBC 로 확인하는 형태다.

- [ ] **Step 2: 실패하는 마이그레이션 테스트를 쓴다**

`FlywayMigrationTest` 에 V17 검증 바로 뒤에 추가한다. 헬퍼 이름은 Step 1에서 읽은 실제 이름을 쓴다.

```kotlin
    @Test
    fun `V18 은 비회원 등록 컬럼과 유니크 인덱스를 추가한다`() {
        val columns = columnsOf("calendar_registration")

        assertTrue(columns.containsKey("kakao_provider_id"), columns.keys.toString())
        assertEquals("YES", columns["kakao_provider_id"]?.nullable)
        assertEquals("YES", columns["user_id"]?.nullable)
        assertTrue(
            indexNamesOf("calendar_registration").contains("uk_calendar_registration_kakao_party_provider"),
            indexNamesOf("calendar_registration").toString(),
        )
    }
```

- [ ] **Step 3: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.db.FlywayMigrationTest"`
Expected: FAIL — `kakao_provider_id` 없음

- [ ] **Step 4: 마이그레이션을 쓴다**

`src/main/resources/db/migration/V18__add_calendar_registration_guest.sql`:

```sql
alter table calendar_registration modify column user_id bigint null;

alter table calendar_registration add column kakao_provider_id varchar(100) null;

create unique index uk_calendar_registration_kakao_party_provider
    on calendar_registration (kakao_provider_id, party_id, provider);
```

- [ ] **Step 5: 엔티티를 고친다**

`CalendarRegistration.kt` 의 생성자와 companion 을 아래로 바꾼다. `linkEvent` 와 `eventId` 는 그대로다.

```kotlin
@Entity
@Table(
    name = "calendar_registration",
    uniqueConstraints = [
        UniqueConstraint(
            name = CalendarRegistration.UK_USER_PARTY_PROVIDER,
            columnNames = ["user_id", "party_id", "provider"],
        ),
        UniqueConstraint(
            name = CalendarRegistration.UK_KAKAO_PARTY_PROVIDER,
            columnNames = ["kakao_provider_id", "party_id", "provider"],
        ),
    ],
)
class CalendarRegistration(
    @Column(name = "user_id")
    val userId: Long? = null,
    @Column(name = "party_id", nullable = false)
    val partyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    val provider: CalendarProvider,
    @Column(name = "kakao_provider_id", length = 100)
    val kakaoProviderId: String? = null,
    eventId: String? = null,
) : BaseEntity() {
```

companion object:

```kotlin
    companion object {
        const val UK_USER_PARTY_PROVIDER = "uk_calendar_registration_user_party_provider"
        const val UK_KAKAO_PARTY_PROVIDER = "uk_calendar_registration_kakao_party_provider"
    }
```

`userId` 가 첫 파라미터이고 기본값을 얻었으므로, 기존 호출(`CalendarRegistration(userId = ..., partyId = ..., provider = ...)`)은 named argument 라 그대로 컴파일된다.

- [ ] **Step 6: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.db.FlywayMigrationTest"`
Expected: PASS

Run: `./gradlew test --tests "com.team2.server.calendar.*"`
Expected: PASS — 기존 캘린더 테스트 회귀 없음

- [ ] **Step 7: 컨테이너 누수를 확인한다**

Run: `docker ps -a --filter "label=org.testcontainers" --format '{{.Names}}'`
Expected: 출력 없음

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V18__add_calendar_registration_guest.sql \
        src/main/kotlin/com/team2/server/calendar/domain/entity/CalendarRegistration.kt \
        src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt
git commit -m "feat: 비회원 캘린더 등록 스키마 추가"
```

---

## Task 3: 비회원 등록 조회·예약을 Service 에 더한다

**Files:**
- Modify: `src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationServiceTest.kt`

**Interfaces:**
- Consumes: `CalendarRegistration` (Task 2), `CalendarProvider.KAKAO_TALK`, `ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS`
- Produces:
  - `CalendarRegistrationRepository.existsByKakaoProviderIdAndPartyIdAndProvider(kakaoProviderId: String, partyId: Long, provider: CalendarProvider): Boolean`
  - `CalendarRegistrationService.existsForGuest(kakaoProviderId: String, partyId: Long): Boolean`
  - `CalendarRegistrationService.reserveForGuest(kakaoProviderId: String, partyId: Long): CalendarRegistration`

`reserveForGuest` 는 유니크 충돌 시 `BusinessException(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS)` 를 던진다 — 기존 `reserve` 와 같은 계약이다. Service 의존성은 1개로 유지되고 public 메서드는 5개가 된다 (제한과 같음).

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationServiceTest.kt` 를 만든다. `JpaSliceTestSupport` 를 상속해 기존 지문에 맞춘다.

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.support.JpaSliceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalendarRegistrationServiceTest
    @Autowired
    constructor(
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
    ) : JpaSliceTestSupport() {
        private val service = CalendarRegistrationService(calendarRegistrationRepository)

        @Test
        fun `비회원 등록을 예약하면 회원번호로 조회된다`() {
            val registration = service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            assertNotNull(registration.id)
            assertEquals("4321", registration.kakaoProviderId)
            assertTrue(service.existsForGuest(kakaoProviderId = "4321", partyId = 170L))
        }

        @Test
        fun `같은 회원번호와 파티로 두 번 예약하면 진행 중 예외가 난다`() {
            service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            val exception =
                runCatching { service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L) }
                    .exceptionOrNull()

            assertEquals(
                ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS,
                (exception as BusinessException).errorCode,
            )
        }

        @Test
        fun `다른 파티면 같은 회원번호로 예약할 수 있다`() {
            service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            val other = service.reserveForGuest(kakaoProviderId = "4321", partyId = 171L)

            assertNotNull(other.id)
        }

        @Test
        fun `등록이 없으면 존재하지 않는다고 답한다`() {
            assertFalse(service.existsForGuest(kakaoProviderId = "9999", partyId = 170L))
        }
    }
```

`JpaSliceTestSupport` 의 실제 패키지가 다르면 맞춘다. Run: `find src/test -name "JpaSliceTestSupport.kt"`

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.CalendarRegistrationServiceTest"`
Expected: 컴파일 실패 — `reserveForGuest` / `existsForGuest` 없음

- [ ] **Step 3: Repository 메서드를 더한다**

`CalendarRegistrationRepository` 에 추가한다 (기존 `findByUserIdAndPartyIdAndProvider` 는 그대로):

```kotlin
    fun existsByKakaoProviderIdAndPartyIdAndProvider(
        kakaoProviderId: String,
        partyId: Long,
        provider: CalendarProvider,
    ): Boolean
```

- [ ] **Step 4: Service 메서드를 더한다**

`CalendarRegistrationService` 에 추가한다:

```kotlin
    fun existsForGuest(
        kakaoProviderId: String,
        partyId: Long,
    ): Boolean =
        calendarRegistrationRepository.existsByKakaoProviderIdAndPartyIdAndProvider(
            kakaoProviderId = kakaoProviderId,
            partyId = partyId,
            provider = CalendarProvider.KAKAO_TALK,
        )

    /** 비회원은 갱신 경로가 없어 예약이 곧 중복 차단이다. 동시 요청은 UNIQUE 제약에서 막힌다. */
    fun reserveForGuest(
        kakaoProviderId: String,
        partyId: Long,
    ): CalendarRegistration =
        try {
            calendarRegistrationRepository.saveAndFlush(
                CalendarRegistration(
                    partyId = partyId,
                    provider = CalendarProvider.KAKAO_TALK,
                    kakaoProviderId = kakaoProviderId,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (e.isConstraintViolation(CalendarRegistration.UK_KAKAO_PARTY_PROVIDER)) {
                throw BusinessException(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS)
            }
            throw e
        }
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.CalendarRegistrationServiceTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/CalendarRegistrationRepository.kt \
        src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt \
        src/test/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationServiceTest.kt
git commit -m "feat: 비회원 캘린더 등록 예약 추가"
```

---

## Task 4: 초대 토큰으로 파티 정보를 읽는다

**Files:**
- Modify: `src/main/kotlin/com/team2/server/calendar/application/port/PartyCalendarInfoPort.kt`
- Modify: `src/main/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapter.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterGuestTest.kt`

**Interfaces:**
- Consumes: `PartyInviteService.findUsableInvite(inviteToken: String, now: LocalDateTime): PartyInvite` (만료 시 `ErrorCode.INVITE_LINK_EXPIRED`, 없으면 `ErrorCode.PARTY_NOT_FOUND`), `PartyQueryService.requireParty(partyId: Long): Party`
- Produces: `PartyCalendarInfoPort.loadForInviteToken(inviteToken: String, now: LocalDateTime): PartyCalendarInfo`

`PartyCalendarInfo` 는 기존 타입을 그대로 쓴다 (`partyId`, `celebrationKind`, `celebrantName`, `startedAt`, `inviteUrl`). 참여자 검증은 하지 않는다 — 초대 토큰 보유가 권한 근거다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

통합 테스트로 쓴다. 어댑터가 세 Service 를 쓰므로 단위 테스트는 스텁이 과해진다. 기존 지문 `@SpringBootTest + @Import(TC)` 에 맞춰 `IntegrationTestSupport` 를 상속한다.

```kotlin
package com.team2.server.calendar.infrastructure.party

import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.common.DatabaseCleanup
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.support.IntegrationTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PartyCalendarInfoAdapterGuestTest
    @Autowired
    constructor(
        private val partyCalendarInfoPort: PartyCalendarInfoPort,
        private val databaseCleanup: DatabaseCleanup,
    ) : IntegrationTestSupport() {
        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `초대 토큰으로 파티 정보를 읽는다`() {
            val startedAt = LocalDateTime.now().plusDays(2)
            val fixture = saveHostPartyAndInvite(startedAt)

            val info = partyCalendarInfoPort.loadForInviteToken(fixture.inviteToken, LocalDateTime.now())

            assertEquals(fixture.partyId, info.partyId)
            assertEquals(startedAt.withNano(0), info.startedAt.withNano(0))
        }

        @Test
        fun `없는 초대 토큰이면 파티를 찾을 수 없다`() {
            val exception =
                runCatching {
                    partyCalendarInfoPort.loadForInviteToken("nosuchtk", LocalDateTime.now())
                }.exceptionOrNull()

            assertEquals(ErrorCode.PARTY_NOT_FOUND, (exception as BusinessException).errorCode)
        }

        @Test
        fun `만료된 초대 토큰이면 만료 예외가 난다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))

            val exception =
                runCatching {
                    partyCalendarInfoPort.loadForInviteToken(
                        fixture.inviteToken,
                        LocalDateTime.now().plusYears(5),
                    )
                }.exceptionOrNull()

            assertEquals(ErrorCode.INVITE_LINK_EXPIRED, (exception as BusinessException).errorCode)
        }
    }
```

픽스처 헬퍼(`saveHostPartyAndInvite`)는 `TalkCalendarControllerTest` 의 `saveHostAndParty` 를 참고해 같은 방식으로 이 클래스 안에 private 으로 만든다. 초대 토큰까지 함께 저장해야 한다.

Run: `grep -n -A30 "private fun saveHostAndParty" src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt` 로 기존 픽스처를 읽고, 초대 생성 방식은 `grep -rn "PartyInvite(" src/test | head` 로 확인한다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.party.PartyCalendarInfoAdapterGuestTest"`
Expected: 컴파일 실패 — `loadForInviteToken` 없음

- [ ] **Step 3: 포트에 메서드를 더한다**

`PartyCalendarInfoPort` 에 추가한다:

```kotlin
    /**
     * 초대 토큰으로 파티 정보를 읽는다. 참여자 여부는 보지 않는다 — 초대 링크 보유가 권한 근거다.
     */
    fun loadForInviteToken(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyCalendarInfo
```

- [ ] **Step 4: 어댑터에 구현을 더한다**

`PartyCalendarInfoAdapter` 에 추가한다. `findInviteUrl` 을 재사용하지 않고 이미 가진 토큰으로 바로 조립한다 — 같은 토큰을 다시 조회할 이유가 없다.

```kotlin
    override fun loadForInviteToken(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyCalendarInfo {
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = partyQueryService.requireParty(invite.party.id)

        return PartyCalendarInfo(
            partyId = party.id,
            celebrationKind = party.purpose.toCelebrationKind(),
            celebrantName = party.celebrantNickname ?: party.name,
            startedAt = party.startedAt,
            inviteUrl = "$webBaseUrl/invite/$inviteToken",
        )
    }
```

`invite.party.id` 접근 경로가 다르면 `PartyInvite` 엔티티를 읽어 맞춘다. Run: `grep -n "party\|partyId" src/main/kotlin/com/team2/server/party/domain/entity/PartyInvite.kt`

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.party.PartyCalendarInfoAdapterGuestTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/port/PartyCalendarInfoPort.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapter.kt \
        src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterGuestTest.kt
git commit -m "feat: 초대 토큰으로 파티 캘린더 정보 조회"
```

---

## Task 5: 인가 코드를 토큰과 회원번호로 바꾼다

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/ExchangeKakaoConsentUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/dto/KakaoConsentCredentials.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/usecase/ExchangeKakaoConsentUseCaseTest.kt`

**Interfaces:**
- Consumes: `KakaoOAuthPort.exchange(code: String, redirectUri: String): KakaoOAuthTokens?`, `KakaoAccountPort.fetchProviderId(accessToken: String): String?`
- Produces:
  - `data class KakaoConsentCredentials(val accessToken: String, val kakaoProviderId: String)`
  - `ExchangeKakaoConsentUseCase.invoke(code: String, redirectUri: String): KakaoConsentCredentials?`

둘 중 하나라도 실패하면 `null` 을 돌려준다. 예외를 던지지 않는 이유는 호출자가 리다이렉트로 사유를 전달해야 하고, 트랜잭션 롤백이 필요한 쓰기가 없기 때문이다. `@Transactional` 을 붙이지 않는다 — 외부 호출만 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeKakaoConsentUseCaseTest {
    private val tokens =
        KakaoOAuthTokens(
            accessToken = "guest-access",
            accessTokenExpiresInSeconds = 21599,
            refreshToken = "guest-refresh",
            refreshTokenExpiresInSeconds = 5183999,
        )

    private fun useCase(
        exchangeResult: KakaoOAuthTokens?,
        providerId: String?,
    ): ExchangeKakaoConsentUseCase =
        ExchangeKakaoConsentUseCase(
            kakaoOAuthPort =
                object : KakaoOAuthPort {
                    override fun exchange(
                        code: String,
                        redirectUri: String,
                    ): KakaoOAuthTokens? = exchangeResult

                    override fun refresh(refreshToken: String): KakaoOAuthTokens? =
                        throw UnsupportedOperationException("이 테스트는 refresh 를 쓰지 않는다")
                },
            kakaoAccountPort = object : KakaoAccountPort {
                override fun fetchProviderId(accessToken: String): String? = providerId
            },
        )

    @Test
    fun `토큰 교환과 회원번호 확인이 성공하면 자격증명을 돌려준다`() {
        val result = useCase(tokens, "4321")("code", "https://api.example.com/callback")

        assertEquals("guest-access", result?.accessToken)
        assertEquals("4321", result?.kakaoProviderId)
    }

    @Test
    fun `카카오가 토큰 교환을 거부하면 null 이다`() {
        assertNull(useCase(null, "4321")("code", "https://api.example.com/callback"))
    }

    @Test
    fun `회원번호를 확인할 수 없으면 null 이다`() {
        assertNull(useCase(tokens, null)("code", "https://api.example.com/callback"))
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.ExchangeKakaoConsentUseCaseTest"`
Expected: 컴파일 실패 — `ExchangeKakaoConsentUseCase` 없음

- [ ] **Step 3: DTO 를 만든다**

`src/main/kotlin/com/team2/server/calendar/application/dto/KakaoConsentCredentials.kt`:

```kotlin
package com.team2.server.calendar.application.dto

/**
 * 동의 콜백에서 확보한 1회용 자격증명.
 * 비회원 경로는 이 값을 저장하지 않고 일정 생성에 쓴 뒤 버린다.
 */
data class KakaoConsentCredentials(
    val accessToken: String,
    val kakaoProviderId: String,
)
```

- [ ] **Step 4: UseCase 를 만든다**

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import org.springframework.stereotype.Service

/**
 * 인가 코드를 액세스 토큰과 카카오 회원번호로 바꾼다.
 *
 * 외부 호출만 하므로 트랜잭션을 걸지 않는다. 실패를 예외가 아니라 `null` 로 알리는 이유는
 * 호출자가 사유를 리다이렉트 파라미터로 전달해야 하고, 되감을 쓰기가 없기 때문이다.
 */
@Service
class ExchangeKakaoConsentUseCase(
    private val kakaoOAuthPort: KakaoOAuthPort,
    private val kakaoAccountPort: KakaoAccountPort,
) {
    operator fun invoke(
        code: String,
        redirectUri: String,
    ): KakaoConsentCredentials? {
        val tokens = kakaoOAuthPort.exchange(code, redirectUri) ?: return null
        val providerId = kakaoAccountPort.fetchProviderId(tokens.accessToken) ?: return null
        return KakaoConsentCredentials(accessToken = tokens.accessToken, kakaoProviderId = providerId)
    }
}
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.ExchangeKakaoConsentUseCaseTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/dto/KakaoConsentCredentials.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/ExchangeKakaoConsentUseCase.kt \
        src/test/kotlin/com/team2/server/calendar/application/usecase/ExchangeKakaoConsentUseCaseTest.kt
git commit -m "feat: 카카오 동의 자격증명 교환 유스케이스 추가"
```

---

## Task 6: 비회원 일정 등록 UseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/dto/GuestCalendarRegistrationResult.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCaseTest.kt`

**Interfaces:**
- Consumes: `PartyCalendarInfoPort.loadForInviteToken` (Task 4), `CalendarRegistrationService.existsForGuest` / `reserveForGuest` / `linkEvent` (Task 3), `TalkCalendarPort.createEvent(accessToken: String, event: CalendarEvent): String`, `PartyCalendarEventPolicy.compose(kind, celebrantName, startedAt, inviteUrl): CalendarEvent`, `KakaoConsentCredentials` (Task 5)
- Produces:
  - `enum class GuestCalendarRegistrationResult { REGISTERED, ALREADY_REGISTERED, PARTY_ALREADY_STARTED, INVITE_INVALID, FAILED }`
  - `RegisterGuestTalkCalendarEventUseCase.invoke(inviteToken: String, credentials: KakaoConsentCredentials): GuestCalendarRegistrationResult`

의존성 4개: `partyCalendarInfoPort`, `calendarRegistrationService`, `talkCalendarPort`, `clock`. `@Transactional` 을 붙인다 — 예약과 `linkEvent` 쓰기가 있다.

테스트는 통합 테스트다 (Pre-flight Ruling 2) — 상속 스텁이 불가능해 실제 Service·Repository 를 쓴다.

결과를 예외가 아니라 enum 으로 돌려주는 이유는 호출자가 리다이렉트 파라미터로 사유를 전달해야 하기 때문이다. 단 `INVITE_INVALID` 는 포트가 던지는 `BusinessException` 을 잡아 변환한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

통합 테스트로 쓴다. `CalendarRegistrationService` 는 Kotlin 기본 `final` 이라 상속 스텁이 불가능하고, `JpaRepository` 스텁은 추상 메서드가 많아 컴파일되지 않는다. 실제 Service·Repository 를 쓰고 카카오만 `KakaoStubServers` 로 대체한다. 기존 지문 `@SpringBootTest + @Import(TC)` 에 맞춰 `IntegrationTestSupport` 를 상속한다.

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.support.IntegrationTestSupport
import com.team2.server.support.KakaoStubServers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegisterGuestTalkCalendarEventUseCaseTest
    @Autowired
    constructor(
        private val registerGuestTalkCalendarEventUseCase: RegisterGuestTalkCalendarEventUseCase,
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
        private val databaseCleanup: DatabaseCleanup,
    ) : IntegrationTestSupport() {
        private val credentials =
            KakaoConsentCredentials(accessToken = "guest-access", kakaoProviderId = "4321")

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            KakaoStubServers.reset()
        }

        @Test
        fun `등록에 성공하면 REGISTERED 이고 비회원 행이 남는다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.REGISTERED, result)
            val saved = calendarRegistrationRepository.findAll().single()
            assertEquals(fixture.partyId, saved.partyId)
            assertEquals("4321", saved.kakaoProviderId)
            assertNull(saved.userId)
            assertNotNull(saved.eventId)
            assertNotNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `이미 등록했으면 카카오를 부르지 않고 ALREADY_REGISTERED 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)
            KakaoStubServers.reset()

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.ALREADY_REGISTERED, result)
            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `다른 회원번호면 같은 파티에 각각 등록된다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            val result =
                registerGuestTalkCalendarEventUseCase(
                    fixture.inviteToken,
                    credentials.copy(kakaoProviderId = "9999"),
                )

            assertEquals(GuestCalendarRegistrationResult.REGISTERED, result)
            assertEquals(2, calendarRegistrationRepository.findAll().size)
        }

        @Test
        fun `파티가 이미 시작됐으면 PARTY_ALREADY_STARTED 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().minusMinutes(1))

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `없는 초대 토큰이면 INVITE_INVALID 다`() {
            val result = registerGuestTalkCalendarEventUseCase("nosuchtk", credentials)

            assertEquals(GuestCalendarRegistrationResult.INVITE_INVALID, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `만료된 초대 토큰이면 INVITE_INVALID 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2), inviteExpired = true)

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.INVITE_INVALID, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }
    }
```

픽스처 `saveHostPartyAndInvite(startedAt, inviteExpired = false)` 는 호스트 사용자·파티·초대를 저장하고 `partyId` 와 `inviteToken` 을 담은 로컬 data class 를 돌려주는 private 헬퍼다. Task 4 에서 만든 같은 이름의 헬퍼와 동일한 형태이므로 그 구현을 이 클래스에도 복제한다 — `inviteExpired` 분기만 추가해 만료 시 `expiresAt` 을 과거로 둔다. 두 테스트 클래스가 공유 베이스를 만들 만큼 크지 않다.

Run: `grep -n -A30 "private fun saveHostPartyAndInvite" src/test/kotlin/com/team2/server/calendar/infrastructure/party/PartyCalendarInfoAdapterGuestTest.kt`

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.RegisterGuestTalkCalendarEventUseCaseTest"`
Expected: 컴파일 실패 — `RegisterGuestTalkCalendarEventUseCase` 없음

- [ ] **Step 3: 결과 enum 을 만든다**

`src/main/kotlin/com/team2/server/calendar/application/dto/GuestCalendarRegistrationResult.kt`:

```kotlin
package com.team2.server.calendar.application.dto

/**
 * 비회원 등록 결과. 호출자가 리다이렉트 파라미터로 사유를 전달하므로 예외가 아니라 값으로 돌려준다.
 */
enum class GuestCalendarRegistrationResult {
    REGISTERED,
    ALREADY_REGISTERED,
    PARTY_ALREADY_STARTED,
    INVITE_INVALID,
    FAILED,
}
```

- [ ] **Step 4: UseCase 를 만든다**

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.policy.PartyCalendarEventPolicy
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 비회원이 동의 한 번으로 파티 일정을 등록한다.
 *
 * 액세스 토큰을 저장하지 않으므로 갱신 경로가 없다. 같은 사람이 다시 요청하면 카카오를 부르지 않고
 * 이미 등록했다고 답한다 — 부르면 캘린더에 같은 일정이 하나 더 쌓인다.
 */
@Service
class RegisterGuestTalkCalendarEventUseCase(
    private val partyCalendarInfoPort: PartyCalendarInfoPort,
    private val calendarRegistrationService: CalendarRegistrationService,
    private val talkCalendarPort: TalkCalendarPort,
    private val clock: Clock,
) {
    @Suppress("ReturnCount")
    @Transactional
    operator fun invoke(
        inviteToken: String,
        credentials: KakaoConsentCredentials,
    ): GuestCalendarRegistrationResult {
        val now = LocalDateTime.now(clock)
        val info =
            try {
                partyCalendarInfoPort.loadForInviteToken(inviteToken, now)
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.PARTY_NOT_FOUND || e.errorCode == ErrorCode.INVITE_LINK_EXPIRED) {
                    return GuestCalendarRegistrationResult.INVITE_INVALID
                }
                throw e
            }

        if (!now.isBefore(info.startedAt)) {
            return GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED
        }
        if (calendarRegistrationService.existsForGuest(credentials.kakaoProviderId, info.partyId)) {
            return GuestCalendarRegistrationResult.ALREADY_REGISTERED
        }

        val registration =
            try {
                calendarRegistrationService.reserveForGuest(credentials.kakaoProviderId, info.partyId)
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS) {
                    return GuestCalendarRegistrationResult.ALREADY_REGISTERED
                }
                throw e
            }

        val event =
            PartyCalendarEventPolicy.compose(
                kind = info.celebrationKind,
                celebrantName = info.celebrantName,
                startedAt = info.startedAt,
                inviteUrl = info.inviteUrl,
            )
        calendarRegistrationService.linkEvent(registration, talkCalendarPort.createEvent(credentials.accessToken, event))
        return GuestCalendarRegistrationResult.REGISTERED
    }
}
```

60줄 제한을 넘으면 `loadInfoOrNull` 같은 private 헬퍼로 try 블록을 빼낸다.

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.RegisterGuestTalkCalendarEventUseCaseTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/dto/GuestCalendarRegistrationResult.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCase.kt \
        src/test/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCaseTest.kt
git commit -m "feat: 비회원 톡캘린더 등록 유스케이스 추가"
```

---

## Task 7: 콜백에서 주체별로 분기한다

**Files:**
- Modify: `src/main/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt` (`ConsentOutcome` 만)
- Modify: `src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentController.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentControllerGuestTest.kt`

**Interfaces:**
- Consumes: `ConsentTicketSigner.verify(ticket): ConsentSubject?` (Task 1), `ExchangeKakaoConsentUseCase.invoke` (Task 5), `RegisterGuestTalkCalendarEventUseCase.invoke` (Task 6), `GuestCalendarRegistrationResult` (Task 6), `KakaoConsentUrlFactory.callbackUri()`
- Produces: `ConsentOutcome` 에 `REGISTERED`, `ALREADY_REGISTERED`, `PARTY_ALREADY_STARTED`, `INVITE_INVALID` 추가

컨트롤러 생성자 의존성이 4개 → 6개가 된다. 컨트롤러에는 레이어드 규칙의 5개 제한이 적용되지 않는다 (UseCase 전용 규칙). 기존 `TalkCalendarController` 도 UseCase 3개를 조합한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.team2.server.calendar.api

import com.team2.server.calendar.application.service.ConsentSubject
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.support.KakaoStubServers
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class KakaoCalendarConsentControllerGuestTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val consentTicketSigner: ConsentTicketSigner,
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            KakaoStubServers.reset()
        }

        @Test
        fun `비회원 동의 콜백은 일정을 만들고 등록 완료로 복귀한다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))

            val location = callback(ticket, code = "kakao-code")

            assertTrue(location.contains("calendarConsent=registered"), location)
            val saved = calendarRegistrationRepository.findAll().single()
            assertEquals(fixture.partyId, saved.partyId)
            assertNotNull(saved.kakaoProviderId)
            assertEquals(null, saved.userId)
            assertNotNull(saved.eventId)
            assertNotNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `같은 비회원이 다시 동의하면 이미 등록으로 복귀한다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))
            callback(ticket, code = "kakao-code")
            KakaoStubServers.reset()

            val location = callback(
                consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken)),
                code = "kakao-code",
            )

            assertTrue(location.contains("calendarConsent=already_registered"), location)
            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertEquals(null, KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `없는 초대 토큰이면 초대 무효로 복귀한다`() {
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest("nosuchtk"))

            val location = callback(ticket, code = "kakao-code")

            assertTrue(location.contains("calendarConsent=invite_invalid"), location)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `동의를 거부하면 거부로 복귀하고 등록이 없다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))

            val location = callback(ticket, code = null)

            assertTrue(location.contains("calendarConsent=denied"), location)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `state 와 쿠키 티켓이 다르면 만료로 복귀한다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))
            val other = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))

            val result =
                mockMvc
                    .get("/api/v1/kakao-calendar/consent/callback") {
                        param("code", "kakao-code")
                        param("state", other)
                        cookie(ticketCookie(ticket), returnPathCookie())
                    }.andReturn()

            val location = result.response.getHeader(HttpHeaders.LOCATION) ?: ""
            assertTrue(location.contains("calendarConsent=expired"), location)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }
    }
```

픽스처(`saveHostPartyAndInvite`), 쿠키 헬퍼(`ticketCookie` / `returnPathCookie`), `callback` 헬퍼는 구현자가 아래를 읽어 만든다:

Run: `cat src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentCookies.kt`
Run: `grep -rn "KakaoCalendarConsentCookies\|consent/callback" src/test --include='*.kt' | head`

기존 콜백 테스트가 있으면 그 쿠키 조립 방식을 그대로 재사용한다. 없으면 `KakaoCalendarConsentCookies` 의 쿠키 이름 상수로 `Cookie(name, value)` 를 만든다. `callback(ticket, code)` 는 `code` 가 null 이면 파라미터를 붙이지 않고, `state` 에는 `ticket` 을 넣고 쿠키에도 같은 `ticket` 을 넣는다.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.KakaoCalendarConsentControllerGuestTest"`
Expected: FAIL — 비회원 티켓이 분기되지 않아 `expired` 또는 `failed` 로 복귀

- [ ] **Step 3: `ConsentOutcome` 을 넓힌다**

`SaveKakaoCalendarConsentUseCase.kt` 상단의 enum 을 바꾼다. 기존 5값은 그대로 두고 아래를 더한다.

```kotlin
enum class ConsentOutcome {
    GRANTED,
    DENIED,
    ACCOUNT_MISMATCH,
    EXPIRED,
    FAILED,

    /** 비회원 경로 전용. 콜백에서 일정 생성까지 끝났으므로 클라이언트가 등록을 재호출하면 안 된다. */
    REGISTERED,
    ALREADY_REGISTERED,
    PARTY_ALREADY_STARTED,
    INVITE_INVALID,
}
```

- [ ] **Step 4: 콜백에 분기를 넣는다**

`KakaoCalendarConsentController` 를 고친다. 생성자에 두 UseCase 를 더한다:

```kotlin
class KakaoCalendarConsentController(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
    private val saveKakaoCalendarConsentUseCase: SaveKakaoCalendarConsentUseCase,
    private val exchangeKakaoConsentUseCase: ExchangeKakaoConsentUseCase,
    private val registerGuestTalkCalendarEventUseCase: RegisterGuestTalkCalendarEventUseCase,
    private val oAuth2Properties: OAuth2Properties,
) {
```

`callback` 의 `verify` 이후를 아래로 바꾼다. 앞단(쿠키 읽기, `code` 검사, `state` 대조)은 그대로 둔다 — 두 주체가 같은 방어를 쓴다.

```kotlin
        val subject =
            consentTicketSigner.verify(cookieTicket)
                ?: return RedirectView(resultUrl(returnPath, ConsentOutcome.EXPIRED))

        val outcome =
            runCatching {
                when (subject) {
                    is ConsentSubject.Member ->
                        saveKakaoCalendarConsentUseCase(code, subject.userId, kakaoConsentUrlFactory.callbackUri())
                    is ConsentSubject.Guest -> registerGuest(code, subject.inviteToken)
                }
            }.onFailure { log.error("카카오 캘린더 동의 처리 실패", it) }
                .getOrDefault(ConsentOutcome.FAILED)
        return RedirectView(resultUrl(returnPath, outcome))
```

private 헬퍼를 더한다:

```kotlin
    /** 비회원은 토큰을 저장하지 않으므로 여기서 등록까지 끝낸다. 토큰은 반환하지 않고 버린다. */
    private fun registerGuest(
        code: String,
        inviteToken: String,
    ): ConsentOutcome {
        val credentials =
            exchangeKakaoConsentUseCase(code, kakaoConsentUrlFactory.callbackUri())
                ?: return ConsentOutcome.FAILED
        return when (registerGuestTalkCalendarEventUseCase(inviteToken, credentials)) {
            GuestCalendarRegistrationResult.REGISTERED -> ConsentOutcome.REGISTERED
            GuestCalendarRegistrationResult.ALREADY_REGISTERED -> ConsentOutcome.ALREADY_REGISTERED
            GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED -> ConsentOutcome.PARTY_ALREADY_STARTED
            GuestCalendarRegistrationResult.INVITE_INVALID -> ConsentOutcome.INVITE_INVALID
            GuestCalendarRegistrationResult.FAILED -> ConsentOutcome.FAILED
        }
    }
```

`enter` 의 `verify(ticket) == null` 검사는 그대로 동작한다 — 두 주체 모두 non-null 을 돌려주므로 변경이 없다.

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.KakaoCalendarConsentControllerGuestTest"`
Expected: PASS

Run: `./gradlew test --tests "com.team2.server.calendar.*"`
Expected: PASS — 회원 콜백 회귀 없음

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt \
        src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentController.kt \
        src/test/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentControllerGuestTest.kt
git commit -m "feat: 동의 콜백에서 비회원 등록 처리"
```

---

## Task 8: 비회원 동의 진입 URL 엔드포인트

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentController.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentApi.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentControllerTest.kt`

**Interfaces:**
- Consumes: `ConsentTicketSigner.issue(ConsentSubject.Guest)` (Task 1), `KakaoConsentUrlFactory.consentEntryUrl(ticket, returnPath)` / `isValidReturnPath(returnPath)`
- Produces: `GET /api/v1/party-invites/{inviteToken}/talk-calendar/consent-url?returnPath=...` → `ApiResponse<KakaoCalendarConsentUrlResult>`

응답 모델은 회원용과 같은 타입을 재사용한다. Run: `grep -rn "KakaoCalendarConsentUrlResult" src/main --include='*.kt' | head -3` 로 정확한 타입과 필드명을 확인한다 (프론트가 `res.data?.consentUrl` 로 읽는다).

초대 토큰의 유효성은 여기서 검증하지 않는다 — 콜백이 `INVITE_INVALID` 로 처리한다. 여기서 검증하면 같은 조회를 두 번 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```kotlin
package com.team2.server.calendar.api

import com.team2.server.config.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GuestTalkCalendarConsentControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `인증 없이도 비회원 동의 URL 을 발급한다`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "/invite/a796ba5f")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.consentUrl") { exists() }
                }
        }

        @Test
        fun `복귀 경로가 다른 호스트로 나가면 400`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "//evil.com")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }

        @Test
        fun `복귀 경로가 슬래시로 시작하지 않으면 400`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "evil.com")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }
    }
```

회원용 `consent-url` 엔드포인트의 검증 실패 응답 코드가 `INVALID_INPUT` 이 아니면 그 코드로 맞춘다. Run: `grep -rn -B5 -A20 "consent-url" src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionController.kt`

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.GuestTalkCalendarConsentControllerTest"`
Expected: FAIL — 401 (엔드포인트 없음 + 인증 요구)

- [ ] **Step 3: SecurityConfig 에 인증 예외를 더한다**

`authorizeHttpRequests` 블록의 `GET /api/v1/party-invites/*` 줄 근처에 추가한다:

```kotlin
                auth
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/party-invites/*/talk-calendar/consent-url",
                    ).permitAll()
```

- [ ] **Step 4: Api 인터페이스와 컨트롤러를 만든다**

`GuestTalkCalendarConsentApi.kt` 는 회원용 `KakaoCalendarConnectionApi` 의 Swagger 작성 방식을 따른다. Run: `cat src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionApi.kt` 로 어노테이션 구성을 읽고 같은 형태로 쓴다. 설명에는 "이 경로는 비회원용이며, 동의를 마치면 콜백에서 등록까지 끝난다. 클라이언트는 복귀 후 등록 API 를 재호출하지 않는다" 를 명시한다.

`GuestTalkCalendarConsentController.kt`:

```kotlin
package com.team2.server.calendar.api

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.service.ConsentSubject
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 비회원이 톡캘린더 동의를 시작하는 경로.
 *
 * 초대 토큰의 유효성은 여기서 보지 않는다. 콜백이 등록 시점에 확인하고 사유를 돌려주므로
 * 여기서 또 조회하면 같은 일을 두 번 한다.
 */
@RestController
@RequestMapping("/api/v1/party-invites")
class GuestTalkCalendarConsentController(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
) : GuestTalkCalendarConsentApi {
    @GetMapping("/{inviteToken}/talk-calendar/consent-url")
    override fun issueConsentUrl(
        @PathVariable inviteToken: String,
        @RequestParam returnPath: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult> {
        if (!kakaoConsentUrlFactory.isValidReturnPath(returnPath)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val ticket = consentTicketSigner.issue(ConsentSubject.Guest(inviteToken))
        return ApiResponse.success(
            HttpStatus.OK,
            KakaoCalendarConsentUrlResult(kakaoConsentUrlFactory.consentEntryUrl(ticket, returnPath)),
        )
    }
}
```

`KakaoCalendarConsentUrlResult` 의 실제 패키지와 생성자 파라미터명을 Step 1에서 확인한 값으로 맞춘다.

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.GuestTalkCalendarConsentControllerTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentApi.kt \
        src/main/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentController.kt \
        src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt \
        src/test/kotlin/com/team2/server/calendar/api/GuestTalkCalendarConsentControllerTest.kt
git commit -m "feat: 비회원 톡캘린더 동의 진입 엔드포인트 추가"
```

---

## Task 9: 전체 검증과 마무리

**Files:**
- Modify: 앞 Task 에서 드러난 문제가 있으면 해당 파일

- [ ] **Step 1: 전체 빌드와 테스트**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 테스트 결과를 집계한다**

Run:
```bash
python3 -c "
import glob,xml.etree.ElementTree as ET
t=f=e=s=0
for p in glob.glob('build/test-results/test/TEST-*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print(f'테스트 {t} 실패 {f} 에러 {e} 스킵 {s}')
"
```
Expected: 실패 0, 에러 0. 스킵은 15 (기존 `@ArchIgnore` 베이스라인) — 그보다 많으면 이번 작업이 테스트를 끈 것이므로 원인을 찾는다.

- [ ] **Step 3: ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL. 실패하면 `./gradlew ktlintFormat` 후 변경분을 커밋한다.

- [ ] **Step 4: 컨테이너 누수를 확인한다**

Run: `docker ps -a --filter "label=org.testcontainers" --format '{{.Names}}'`
Expected: 출력 없음

- [ ] **Step 5: 레이어드 규칙을 직접 센다**

Run:
```bash
wc -l src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCase.kt \
      src/main/kotlin/com/team2/server/calendar/application/usecase/ExchangeKakaoConsentUseCase.kt \
      src/main/kotlin/com/team2/server/calendar/application/service/CalendarRegistrationService.kt
```
Expected: 등록 UseCase 60줄 이내(주석 제외 기준으로 판단), Service 150줄 이내. 넘으면 private 헬퍼로 분리한다.

- [ ] **Step 6: 스펙과 대조한다**

`docs/superpowers/specs/2026-10-14-guest-talk-calendar-design.md` 를 다시 읽고 구현이 벗어난 곳이 없는지 확인한다. 특히 토큰이 어디에도 저장되지 않는지 — `kakao_calendar_connection` 에 비회원 행이 생기지 않아야 한다.

Run: `grep -rn "kakaoCalendarConnectionService\|KakaoCalendarConnection" src/main/kotlin/com/team2/server/calendar/application/usecase/RegisterGuestTalkCalendarEventUseCase.kt`
Expected: 출력 없음

- [ ] **Step 7: 커밋이 남아 있으면 커밋한다**

```bash
git status --short
```
깨끗해야 한다. 남은 변경이 있으면 파일을 개별 지정해 커밋한다.

---

## Self-Review

**스펙 커버리지**

| 스펙 요구 | Task |
|---|---|
| 동의 진입 엔드포인트 1개 | 8 |
| 콜백에서 일정 생성 후 토큰 폐기 | 6, 7 |
| 카카오 회원번호 기준 중복 차단 | 2, 3, 6 |
| 사유별 복귀 결과 | 6, 7 |
| 티켓 주체 구분 | 1 |
| 초대 토큰 권한 근거 | 4 |
| 스키마 변경 1개 | 2 |
| 회원 플로우 무변경 | 7 (회귀 테스트), 9 Step 6 |
| 참여자 집계 안 함 | 전체 — participant 를 만드는 Task 가 없다 |

범위 외 항목(일정 수정, 회원 전환 이어받기, 등록 취소)에 대응하는 Task 가 없는 것이 의도대로다.

**타입 일관성**

`ConsentSubject.Member` / `.Guest` (Task 1) → Task 7 분기에서 동일. `KakaoConsentCredentials` (Task 5) → Task 6·7 에서 동일. `GuestCalendarRegistrationResult` 5값 (Task 6) → Task 7 `when` 이 5값을 모두 처리. `CalendarRegistration.UK_KAKAO_PARTY_PROVIDER` (Task 2) → Task 3 에서 참조. `loadForInviteToken` (Task 4) → Task 6 에서 호출.

**알려진 불확실성** — 구현자가 Run 명령으로 확인하도록 각 Task 에 적어둔 것들: `JwtProperties` 생성자 파라미터명(Task 1), `JpaSliceTestSupport` 패키지(Task 3), `PartyInvite.party` 접근 경로(Task 4), `CalendarRegistrationService` 의 `open` 여부(Task 6 — 아니면 통합 테스트로 전환), `KakaoCalendarConsentCookies` 쿠키 이름(Task 7), `KakaoCalendarConsentUrlResult` 필드명(Task 8).
