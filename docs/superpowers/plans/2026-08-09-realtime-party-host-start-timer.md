# 실시간 파티 주최자 시작 기준 타이머 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티의 라이브 10분을 예약 시각이 아니라 주최자가 파티를 시작한 시점부터 흐르게 한다.

> **실행 후 정정 (2026-08-11)** — 아래 Task 1은 컬럼 rename으로 작성되어 있으나, 실제로는 **컬럼 추가 방식으로 변경해 구현했다.** 이 프로젝트는 블루/그린 배포를 공유 DB 하나에 대해 수행하므로(`scripts/deploy.sh`) rename하면 구슬롯이 `Unknown column`으로 죽는다. 최종 마이그레이션은 `V13__add_realtime_party_live_started_at.sql`이며 `live_started_at`을 추가하고 `COALESCE(host_entered_at, started_at)`으로 백필한 뒤 `host_entered_at`을 남겨둔다. 제거는 후속 릴리즈의 별도 마이그레이션 몫이다. 최신 설계는 스펙 문서를 따른다.

**Architecture:** `realtime_party`에 `live_started_at`을 추가하고 기록 지점을 채팅 입장에서 `POST /phase/advance`(ENTRY→MUSIC)로 옮긴다. `RealtimeParty.automaticEndingStartedAt()`이 시작 시각 유무에 따라 `liveStartedAt + 10분` 또는 `startedAt + 30분`(마감선)을 반환하고, 나머지 파생 계산은 모두 이 함수를 경유하므로 자동으로 따라온다. `PartyEndScheduler`는 파티 생성 시 마감선에 fallback 태스크를 걸고, 시작 이벤트를 받으면 취소 후 재스케줄한다.

**Tech Stack:** Kotlin, Spring Boot, JPA/Hibernate, Flyway, MySQL 8.0, JUnit5 + mockito-kotlin, Testcontainers

## Global Constraints

- 스펙: `docs/superpowers/specs/2026-08-09-realtime-party-host-start-timer-design.md`
- 이슈: #247 / 브랜치: `feature/realtime-party-host-start-timer` (이미 checkout 상태)
- `LIVE_DURATION_MINUTES = 10`, `LIVE_END_COUNTDOWN_SECONDS = 60`, `HOST_FAREWELL_AVAILABLE_AFTER_MINUTES = 4`, `ENTERABLE_BEFORE_MINUTES = 5` 는 기존 값 유지
- 신규 상수 `START_GRACE_MINUTES = 30`
- `RealtimePartyStatus` enum에 값을 추가하지 않는다 — 대기 구간도 `LIVE_OPEN`
- 프론트엔드 저장소(`18th-team2-web`)는 건드리지 않는다
- `GetUpcomingPartiesUseCase.kt`는 수정하지 않는다 — `:42`의 `hostRollingPaperOpenAt`이 미시작 파티에서 `startedAt + 30분`으로 표시되는 것은 스펙이 의도한 동작이고, `:73`의 `liveEndAt`은 예정 안내용이라 `startedAt + 10분`을 유지한다
- 커밋 메시지: `<type>: <한국어 명사형 설명>`, 50자 이내, 마침표 없음, scope 없음
- `git add -A` / `git add .` 금지 — 파일 개별 지정
- `--no-verify` 금지
- 테스트 규칙은 `docs/testing-rules.md` 준수. `@SpringBootTest` / `@DataJpaTest`는 `TestcontainersConfiguration` 경유
- 전체 검증: `./gradlew build` / 단일 테스트: `./gradlew test --tests "<FQCN>"`
- ktlint + detekt가 빌드에 물려 있으므로 포맷 위반 시 빌드가 깨진다

---

### Task 1: 시작 시각 기록 지점 이동 (rename + 앵커 이동)

컬럼과 필드를 rename하고, 기록 지점을 채팅 입장에서 phase advance로 옮긴다.
이 태스크가 끝나면 `hostFarewellAvailableAt`이 "파티 시작 + 4분" 기준이 된다.
종료 시각 앵커는 아직 `startedAt + 10분` 그대로다 (Task 2에서 변경).

**Files:**
- Create: `src/main/resources/db/migration/V13__rename_host_entered_at_to_live_started_at.sql`
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCase.kt`
- Delete: `src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyHostEnteredUseCase.kt`
- Delete: `src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyHostEnteredUseCaseTest.kt`
- Create: `src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCaseTest.kt`
- Modify: `src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt:25-26,69-70`
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt:36-48`
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyService.kt:141-144`
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt:9,21,56,50-58`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt`
- Test: `src/test/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepositoryTest.kt:74-101`
- Test: `src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt:88-91`
- Test (rename만): `src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt`, `src/test/kotlin/com/team2/server/party/domain/entity/PartyStatusTest.kt`, `src/test/kotlin/com/team2/server/party/api/PartyControllerTest.kt`, `src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt`, `src/test/kotlin/com/team2/server/party/application/usecase/StartRealtimePartyEndUseCaseTest.kt`, `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`, `src/test/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCaseTest.kt`, `src/test/kotlin/com/team2/server/burstgame/application/usecase/GetCandleBlowStateUseCaseTest.kt`

**Interfaces:**
- Produces:
  - `RealtimeParty.liveStartedAt: LocalDateTime?` (컬럼 `live_started_at`)
  - `PartyRepository.markLiveStartedIfAbsent(partyId: Long, liveStartedAt: LocalDateTime): Int`
  - `PartyService.markLiveStartedIfAbsent(partyId: Long, liveStartedAt: LocalDateTime): Boolean`
  - `MarkRealtimePartyStartedUseCase.invoke(partyId: Long, liveStartedAt: LocalDateTime): LocalDateTime?`

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V13__rename_host_entered_at_to_live_started_at.sql`:

```sql
ALTER TABLE realtime_party
    RENAME COLUMN host_entered_at TO live_started_at;

UPDATE realtime_party realtime_party
    JOIN party party ON party.id = realtime_party.id
SET realtime_party.live_started_at = party.started_at
WHERE party.started_at <= NOW();
```

- [ ] **Step 2: 마이그레이션 검증 테스트 작성**

`FlywayMigrationTest.kt:88-91`의 컬럼 검증을 바꾼다:

```kotlin
            assertEquals(
                ColumnDefinition(dataType = "datetime", datetimePrecision = 6, nullable = true),
                connection.findColumn("realtime_party", "live_started_at"),
            )
```

같은 파일에 백필 조건 테스트를 추가한다 (기존 `Flyway migration backfills existing realtime party ending reasons` 테스트 바로 아래):

```kotlin
    @Test
    fun `Flyway migration backfills live started at only for parties already started`() {
        val flywayToV12 =
            Flyway
                .configure()
                .dataSource(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password)
                .locations("classpath:db/migration")
                .target("12")
                .cleanDisabled(false)
                .load()
        flywayToV12.clean()
        flywayToV12.migrate()

        DriverManager.getConnection(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    insert into users (
                        id, created_at, updated_at, name, birth_day, provider, provider_id, email
                    ) values (
                        1, '2026-06-08 19:00:00', '2026-06-08 19:00:00',
                        'host', '01-01', 'KAKAO', 'provider-id', 'host@example.com'
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    insert into party (
                        id, party_option, created_at, updated_at, owner_id, started_at
                    ) values
                        (1, 'REALTIME', NOW(), NOW(), 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
                        (2, 'REALTIME', NOW(), NOW(), 1, DATE_ADD(NOW(), INTERVAL 1 DAY))
                    """.trimIndent(),
                )
                statement.executeUpdate("insert into realtime_party (id) values (1), (2)")
            }

            Flyway
                .configure()
                .dataSource(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            assertEquals(true, connection.hasLiveStartedAt(1L))
            assertEquals(false, connection.hasLiveStartedAt(2L))
        }
    }
```

파일 하단의 private helper 모음에 다음을 추가한다:

```kotlin
    private fun java.sql.Connection.hasLiveStartedAt(partyId: Long): Boolean =
        prepareStatement("select live_started_at from realtime_party where id = ?").use { statement ->
            statement.setLong(1, partyId)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getTimestamp("live_started_at") != null
            }
        }
```

- [ ] **Step 3: 마이그레이션 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.db.FlywayMigrationTest"`
Expected: FAIL — `live_started_at` 컬럼이 없어 `findColumn`이 null을 반환하고, `hasLiveStartedAt` 조회가 `Unknown column` 으로 실패

- [ ] **Step 4: 엔티티 필드 rename**

`RealtimeParty.kt:25-26`:

```kotlin
    @Column(name = "live_started_at")
    var liveStartedAt: LocalDateTime? = null,
```

`RealtimeParty.kt:69-70`:

```kotlin
    val hostFarewellAvailableAt: LocalDateTime?
        get() = liveStartedAt?.plusMinutes(HOST_FAREWELL_AVAILABLE_AFTER_MINUTES)
```

생성자 파라미터 순서는 그대로 두고 이름만 바꾼다 (`hostEnteredAt` → `liveStartedAt`).

- [ ] **Step 5: 마이그레이션 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.db.FlywayMigrationTest"`
Expected: PASS

- [ ] **Step 6: Repository 조건부 UPDATE rename**

`PartyRepository.kt:36-48`을 교체한다:

```kotlin
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE RealtimeParty party
        SET party.liveStartedAt = :liveStartedAt
        WHERE party.id = :partyId
          AND party.liveStartedAt IS NULL
        """,
    )
    fun markLiveStartedIfAbsent(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): Int
```

- [ ] **Step 7: Repository 테스트 수정**

`PartyRepositoryTest.kt:74-101`을 교체한다:

```kotlin
        @Test
        fun `markLiveStartedIfAbsent는 liveStartedAt을 한 번만 저장한다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))
            val firstLiveStartedAt = BASE_TIME
            val secondLiveStartedAt = BASE_TIME.plusSeconds(5)

            val firstUpdated = partyRepository.markLiveStartedIfAbsent(party.id, firstLiveStartedAt)
            val secondUpdated = partyRepository.markLiveStartedIfAbsent(party.id, secondLiveStartedAt)
            entityManager.flush()
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, firstUpdated)
            assertEquals(0, secondUpdated)
            assertEquals(firstLiveStartedAt, found.liveStartedAt)
        }

        private fun realtimeParty(
            startedAt: LocalDateTime,
            liveEndingStartedAt: LocalDateTime? = null,
            liveStartedAt: LocalDateTime? = null,
        ): RealtimeParty =
            RealtimeParty(
                ownerId = 1L,
                name = "테스트파티",
                startedAt = startedAt,
                liveEndingStartedAt = liveEndingStartedAt,
                liveStartedAt = liveStartedAt,
            )
```

- [ ] **Step 8: Repository 테스트 실행**

Run: `./gradlew test --tests "com.team2.server.party.infrastructure.persistence.PartyRepositoryTest"`
Expected: PASS

- [ ] **Step 9: Service 메서드 rename**

`PartyService.kt:141-144`:

```kotlin
    fun markLiveStartedIfAbsent(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): Boolean = partyRepository.markLiveStartedIfAbsent(partyId, liveStartedAt) == 1
```

- [ ] **Step 10: UseCase 테스트 작성**

`MarkRealtimePartyHostEnteredUseCaseTest.kt`를 삭제하고
`src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCaseTest.kt`를 만든다:

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkRealtimePartyStartedUseCaseTest {
    private val partyService: PartyService = mock()
    private val useCase = MarkRealtimePartyStartedUseCase(partyService)

    @Test
    fun `파티 시작 시각을 처음 저장하면 반환한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(true)

        val result = useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        assertEquals(liveStartedAt, result)
    }

    @Test
    fun `이미 저장되어 있으면 null을 반환한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(false)

        val result = useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        assertNull(result)
    }
}
```

- [ ] **Step 11: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.usecase.MarkRealtimePartyStartedUseCaseTest"`
Expected: FAIL — `MarkRealtimePartyStartedUseCase` 클래스가 없어 컴파일 에러

- [ ] **Step 12: UseCase 구현**

`MarkRealtimePartyHostEnteredUseCase.kt`를 삭제하고
`src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCase.kt`를 만든다:

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkRealtimePartyStartedUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): LocalDateTime? {
        if (!partyService.markLiveStartedIfAbsent(partyId, liveStartedAt)) return null
        return liveStartedAt
    }
}
```

- [ ] **Step 13: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.usecase.MarkRealtimePartyStartedUseCaseTest"`
Expected: PASS

- [ ] **Step 14: 채팅 입장의 마킹 제거**

`EnterRealtimePartyUseCase.kt`에서 다음을 제거한다:
- `import com.team2.server.party.application.usecase.MarkRealtimePartyHostEnteredUseCase` (`:9`)
- 생성자 파라미터 `private val markRealtimePartyHostEnteredUseCase: MarkRealtimePartyHostEnteredUseCase,` (`:21`)
- `markHostEnteredIfNeeded(realtimeParty, entryProfile, now)` 호출 (`:36`)
- `private fun markHostEnteredIfNeeded(...)` 함수 전체 (`:50-58`)

제거 후 생성자 의존성은 `partyInviteService`, `profileResolver`, `resolveRealtimePartyEndingInfoUseCase`, `clock` 4개다.

`EnterRealtimePartyUseCaseTest.kt`에서 주최자 입장 마킹을 검증하는 테스트 3개(`:141`, `:155`, `:161-171` 부근)를 삭제하고,
mock 주입에서 `markRealtimePartyHostEnteredUseCase`를 제거한다.

- [ ] **Step 15: AdvancePartyPhaseUseCase 테스트 작성**

`AdvancePartyPhaseUseCaseTest.kt` 상단의 mock/생성자에 다음을 추가한다:

```kotlin
    private val markRealtimePartyStartedUseCase: MarkRealtimePartyStartedUseCase = mock()
    private val useCase =
        AdvancePartyPhaseUseCase(
            partyService,
            participantService,
            phaseTransitionService,
            markRealtimePartyStartedUseCase,
            clock,
        )
```

그리고 테스트 3개를 추가한다:

```kotlin
    @Test
    fun `ENTRY→MUSIC 전환에 성공하면 파티 시작 시각을 기록한다`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        wheneverTransitionSucceeds(partyId, PartyPhase.ENTRY, PartyPhase.MUSIC)

        useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        verify(markRealtimePartyStartedUseCase).invoke(partyId, fixedNow)
    }

    @Test
    fun `ENTRY→MUSIC 전환에 실패하면 파티 시작 시각을 기록하지 않는다`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(
            phaseTransitionService.advance(
                eq(partyId),
                eq(PartyPhase.ENTRY),
                eq(PartyPhase.MUSIC),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(false)
        whenever(phaseTransitionService.getEntry(partyId))
            .thenReturn(PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow.minusMinutes(1)))

        useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        verify(markRealtimePartyStartedUseCase, never()).invoke(any(), any())
    }

    @Test
    fun `MUSIC→CANDLE 전환은 파티 시작 시각을 기록하지 않는다`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        wheneverTransitionSucceeds(partyId, PartyPhase.MUSIC, PartyPhase.CANDLE)

        useCase(partyId, userId = 10L, participantToken = null, currentPhase = PartyPhase.MUSIC)

        verify(markRealtimePartyStartedUseCase, never()).invoke(any(), any())
    }
```

- [ ] **Step 16: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.usecase.AdvancePartyPhaseUseCaseTest"`
Expected: FAIL — `AdvancePartyPhaseUseCase` 생성자가 4개 인자만 받아 컴파일 에러

- [ ] **Step 17: AdvancePartyPhaseUseCase 구현**

`AdvancePartyPhaseUseCase.kt`의 클래스 본문을 아래로 교체한다.
`import com.team2.server.party.domain.entity.RealtimeParty` 를 추가한다.

```kotlin
@Service
class AdvancePartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseTransitionService: PartyPhaseTransitionService,
    private val markRealtimePartyStartedUseCase: MarkRealtimePartyStartedUseCase,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        currentPhase: PartyPhase,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        val nextPhase =
            ALLOWED_TRANSITIONS[currentPhase]
                ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        validateActor(party, currentPhase, userId, participantToken)

        val advanced = phaseTransitionService.advance(partyId, currentPhase, nextPhase, now, userId, participantToken)
        if (!advanced) return unchangedResult(partyId, party, now)

        if (currentPhase == PartyPhase.ENTRY) {
            markRealtimePartyStartedUseCase(partyId, now)
        }

        return PartyPhaseResult(
            partyId = partyId,
            phase = nextPhase,
            phaseStartedAt = now,
            serverNow = now,
        )
    }

    private fun validateActor(
        party: RealtimeParty,
        currentPhase: PartyPhase,
        userId: Long?,
        participantToken: String?,
    ) {
        when (currentPhase) {
            PartyPhase.ENTRY -> if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            PartyPhase.MUSIC,
            PartyPhase.CANDLE,
            -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }
    }

    private fun unchangedResult(
        partyId: Long,
        party: RealtimeParty,
        now: LocalDateTime,
    ): PartyPhaseResult {
        val entry = phaseTransitionService.getEntry(partyId)
        return PartyPhaseResult(
            partyId = partyId,
            phase = entry?.phase ?: PartyPhase.ENTRY,
            phaseStartedAt = entry?.startedAt ?: party.startedAt,
            serverNow = now,
        )
    }

    companion object {
        val ALLOWED_TRANSITIONS =
            mapOf(
                PartyPhase.ENTRY to PartyPhase.MUSIC,
                PartyPhase.MUSIC to PartyPhase.CANDLE,
                PartyPhase.CANDLE to PartyPhase.BURST,
            )
    }
}
```

- [ ] **Step 18: 남은 테스트 픽스처 rename**

다음 파일에서 `hostEnteredAt` 을 `liveStartedAt` 으로 바꾼다 (의미 변경 없음, 이름만):

- `PartyStatusTest.kt:168,169,173,179,180,184,190,200,201,204,216`
- `PartyControllerTest.kt:293,296,316`
- `PartyPhaseControllerTest.kt:233`
- `StartRealtimePartyEndUseCaseTest.kt:82,111`
- `BurstGameControllerTest.kt:513,542`
- `StartBurstGameUseCaseTest.kt:31,88,89`
- `GetCandleBlowStateUseCaseTest.kt:86,93,94`

- [ ] **Step 19: 전체 빌드 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (ktlint / detekt 포함)

- [ ] **Step 20: 컨테이너 누수 확인**

Run: `docker ps -a --filter "label=org.testcontainers"`
Expected: 결과 0개

- [ ] **Step 21: 커밋**

```bash
git add src/main/resources/db/migration/V13__rename_host_entered_at_to_live_started_at.sql \
        src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt \
        src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt \
        src/main/kotlin/com/team2/server/party/application/service/PartyService.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCase.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyHostEnteredUseCase.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt \
        src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt \
        src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt \
        src/test/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepositoryTest.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCaseTest.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyHostEnteredUseCaseTest.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt \
        src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt \
        src/test/kotlin/com/team2/server/party/domain/entity/PartyStatusTest.kt \
        src/test/kotlin/com/team2/server/party/api/PartyControllerTest.kt \
        src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/StartRealtimePartyEndUseCaseTest.kt \
        src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt \
        src/test/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCaseTest.kt \
        src/test/kotlin/com/team2/server/burstgame/application/usecase/GetCandleBlowStateUseCaseTest.kt
git commit -m "refactor: 파티 시작 시각 기록 지점을 페이즈 전환으로 이동"
```

---

### Task 2: 마감선 도입과 스케줄 재무장

종료 시각 앵커를 시작 시각으로 바꾸고, 미시작 파티를 `startedAt + 30분`에 종료시킨다.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt:34,91-97`
- Modify: `src/main/kotlin/com/team2/server/party/application/event/RealtimePartyEvents.kt`
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt:76-79`
- Test: `src/test/kotlin/com/team2/server/party/domain/entity/PartyStatusTest.kt`
- Test: `src/test/kotlin/com/team2/server/party/infrastructure/sse/PartyEndSchedulerTest.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 1의 `RealtimeParty.liveStartedAt`, `MarkRealtimePartyStartedUseCase.invoke`
- Produces:
  - `RealtimeParty.START_GRACE_MINUTES: Long = 30`
  - `RealtimeParty.startDeadlineAt(): LocalDateTime`
  - `RealtimePartyStartedEvent(partyId: Long, liveStartedAt: LocalDateTime)`
  - `PartyEndScheduler.onRealtimePartyStarted(event: RealtimePartyStartedEvent)`

- [ ] **Step 1: 도메인 테스트 작성**

`PartyStatusTest.kt`에 추가한다 (`liveStart = birthday.atTime(23, 0)` 는 기존 필드):

```kotlin
    @Test
    fun `RealtimeParty - 시작 시각이 있으면 시작 10분 후 종료가 시작된다`() {
        val liveStartedAt = liveStart.plusMinutes(3)
        val party = realtimeParty().apply { this.liveStartedAt = liveStartedAt }

        assertEquals(liveStartedAt.plusMinutes(10), party.automaticEndingStartedAt())
    }

    @Test
    fun `RealtimeParty - 시작 시각이 없으면 startedAt 30분 후가 마감선이다`() {
        val party = realtimeParty()

        assertEquals(liveStart.plusMinutes(30), party.automaticEndingStartedAt())
        assertEquals(liveStart.plusMinutes(30), party.startDeadlineAt())
    }

    @Test
    fun `RealtimeParty - 미시작 대기 구간은 LIVE_OPEN이다`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyStatus.LIVE_OPEN, party.status(liveStart.plusMinutes(29)))
    }

    @Test
    fun `RealtimeParty - 마감선에 도달하면 LIVE_ENDING이다`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(liveStart.plusMinutes(30)))
    }

    @Test
    fun `RealtimeParty - 미시작이면 작별인사 시각이 없다`() {
        val party = realtimeParty()

        assertEquals(null, party.hostFarewellAvailableAt)
    }
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.domain.entity.PartyStatusTest"`
Expected: FAIL — `startDeadlineAt` 미정의로 컴파일 에러, 그리고 `automaticEndingStartedAt()`이 `startedAt+10분`을 반환

- [ ] **Step 3: 도메인 구현**

`RealtimeParty.kt:34`를 교체하고 그 위에 `startDeadlineAt()`을 추가한다:

```kotlin
    fun startDeadlineAt(): LocalDateTime = startedAt.plusMinutes(START_GRACE_MINUTES)

    fun automaticEndingStartedAt(): LocalDateTime =
        liveStartedAt?.plusMinutes(LIVE_DURATION_MINUTES) ?: startDeadlineAt()
```

companion object(`:91-97`)에 상수를 추가한다:

```kotlin
        const val START_GRACE_MINUTES: Long = 30
```

- [ ] **Step 4: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.domain.entity.PartyStatusTest"`
Expected: PASS

- [ ] **Step 5: 이벤트 발행 테스트 작성**

`MarkRealtimePartyStartedUseCaseTest.kt`의 mock/생성자를 바꾸고 테스트를 추가한다:

```kotlin
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val useCase = MarkRealtimePartyStartedUseCase(partyService, applicationEventPublisher)

    @Test
    fun `처음 저장하면 시작 이벤트를 발행한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(true)

        useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        verify(applicationEventPublisher).publishEvent(RealtimePartyStartedEvent(1L, liveStartedAt))
    }

    @Test
    fun `이미 저장되어 있으면 이벤트를 발행하지 않는다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(false)

        useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        verify(applicationEventPublisher, never()).publishEvent(any<RealtimePartyStartedEvent>())
    }
```

import 추가: `com.team2.server.party.application.event.RealtimePartyStartedEvent`,
`org.springframework.context.ApplicationEventPublisher`,
`org.mockito.kotlin.any`, `org.mockito.kotlin.never`, `org.mockito.kotlin.verify`

- [ ] **Step 6: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.usecase.MarkRealtimePartyStartedUseCaseTest"`
Expected: FAIL — `RealtimePartyStartedEvent` 미정의, 생성자 인자 개수 불일치로 컴파일 에러

- [ ] **Step 7: 이벤트 정의와 발행 구현**

`RealtimePartyEvents.kt`에 추가한다:

```kotlin
data class RealtimePartyStartedEvent(
    val partyId: Long,
    val liveStartedAt: LocalDateTime,
)
```

`MarkRealtimePartyStartedUseCase.kt`를 교체한다:

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.event.RealtimePartyStartedEvent
import com.team2.server.party.application.service.PartyService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkRealtimePartyStartedUseCase(
    private val partyService: PartyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): LocalDateTime? {
        if (!partyService.markLiveStartedIfAbsent(partyId, liveStartedAt)) return null
        applicationEventPublisher.publishEvent(RealtimePartyStartedEvent(partyId, liveStartedAt))
        return liveStartedAt
    }
}
```

- [ ] **Step 8: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.usecase.MarkRealtimePartyStartedUseCaseTest"`
Expected: PASS

- [ ] **Step 9: 스케줄러 테스트 수정 및 추가**

`PartyEndSchedulerTest.kt`의 기존 테스트 두 개에서 자동 종료 시각 계산을 마감선으로 바꾼다.
`created event schedules automatic ending and sends ending events`와
`automatic ending returning null does not schedule ending events` 양쪽의

```kotlin
        val endingStartedAt = startedAt.plusMinutes(10)
```

를 다음으로 바꾼다:

```kotlin
        val endingStartedAt = startedAt.plusMinutes(30)
```

그리고 새 테스트를 추가한다:

```kotlin
    @Test
    fun `started event reschedules automatic ending to ten minutes after live start`() {
        val startedAt = now.minusMinutes(10)
        val liveStartedAt = now.minusMinutes(2)
        val endingStartedAt = liveStartedAt.plusMinutes(10)
        val target =
            RealtimeEndingScheduleTarget(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endingStartedAt.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                startedNow = true,
            )
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(target)

        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduler.onRealtimePartyStarted(RealtimePartyStartedEvent(1L, liveStartedAt))
        scheduledTasks[1].run()

        verify(scheduledFutures[0]).cancel(false)
        verify(realtimePartyEventBroadcaster).broadcastPartyEnding(
            partyId = eq(1L),
            endingStartedAt = eq(endingStartedAt),
            endedAt = eq(endingStartedAt.plusSeconds(60)),
            endingReason = eq(RealtimePartyEndingReason.TIME_LIMIT_REACHED),
            hostNickname = eq("주최자"),
        )
    }
```

import 추가: `com.team2.server.party.application.event.RealtimePartyStartedEvent`

- [ ] **Step 10: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.infrastructure.sse.PartyEndSchedulerTest"`
Expected: FAIL — `onRealtimePartyStarted` 미정의로 컴파일 에러

- [ ] **Step 11: 스케줄러 구현**

`PartyEndScheduler.kt:76-79`를 교체한다:

```kotlin
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRealtimePartyCreated(event: RealtimePartyCreatedEvent) {
        scheduleAutomaticEnd(event.partyId, event.startedAt.plusMinutes(RealtimeParty.START_GRACE_MINUTES))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRealtimePartyStarted(event: RealtimePartyStartedEvent) {
        scheduleAutomaticEnd(event.partyId, event.liveStartedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES))
    }
```

import 추가: `com.team2.server.party.application.event.RealtimePartyStartedEvent`

- [ ] **Step 12: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.infrastructure.sse.PartyEndSchedulerTest"`
Expected: PASS

- [ ] **Step 13: 전체 빌드 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt \
        src/main/kotlin/com/team2/server/party/application/event/RealtimePartyEvents.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCase.kt \
        src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt \
        src/test/kotlin/com/team2/server/party/domain/entity/PartyStatusTest.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/MarkRealtimePartyStartedUseCaseTest.kt \
        src/test/kotlin/com/team2/server/party/infrastructure/sse/PartyEndSchedulerTest.kt
git commit -m "feat: 파티 시작 기준 종료 타이머와 미시작 마감선 적용"
```

---

### Task 3: 재기동 복구 경로에 마감선 반영

서버 재기동 시 놓친 종료를 일괄 처리하는 쿼리와, 복구 대상 조회 범위를 새 규칙에 맞춘다.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt:64-86`
- Modify: `src/main/kotlin/com/team2/server/party/application/service/RealtimePartyEndService.kt:60-81`
- Test: `src/test/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepositoryTest.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/service/RealtimePartyEndServiceTest.kt`

**Interfaces:**
- Consumes: Task 2의 `RealtimeParty.START_GRACE_MINUTES`
- Produces: `PartyRepository.startAutomaticRealtimeEndings(now, liveDurationMinutes, startGraceMinutes, partyEndedAfterDays, endingReason): Int`

- [ ] **Step 1: 일괄 종료 쿼리 테스트 작성**

`PartyRepositoryTest.kt`의 기존 테스트 `startAutomaticRealtimeEndings는 전달받은 종료 사유를 저장한다` 를
아래 3개로 교체한다:

```kotlin
        @Test
        fun `startAutomaticRealtimeEndings는 시작된 파티를 시작 10분 후로 종료한다`() {
            val liveStartedAt = BASE_TIME.minusMinutes(10)
            val party =
                partyRepository.save(
                    realtimeParty(startedAt = BASE_TIME.minusMinutes(20), liveStartedAt = liveStartedAt),
                )

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, updated)
            assertEquals(liveStartedAt.plusMinutes(10), found.liveEndingStartedAt)
        }

        @Test
        fun `startAutomaticRealtimeEndings는 미시작 파티를 마감선에 종료한다`() {
            val startedAt = BASE_TIME.minusMinutes(30)
            val party = partyRepository.save(realtimeParty(startedAt = startedAt))

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, updated)
            assertEquals(startedAt.plusMinutes(30), found.liveEndingStartedAt)
            assertEquals(RealtimePartyEndingReason.TIME_LIMIT_REACHED, found.liveEndingReason)
        }

        @Test
        fun `startAutomaticRealtimeEndings는 마감선 전 미시작 파티를 종료하지 않는다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(29)))

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(0, updated)
            assertEquals(null, found.liveEndingStartedAt)
        }
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.infrastructure.persistence.PartyRepositoryTest"`
Expected: FAIL — `startGraceMinutes` 파라미터가 없어 컴파일 에러

- [ ] **Step 3: 쿼리 구현**

`PartyRepository.kt:64-86`을 교체한다:

```kotlin
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            UPDATE realtime_party realtime_party
            JOIN party party ON party.id = realtime_party.id
            SET realtime_party.live_ending_started_at = COALESCE(
                    DATE_ADD(realtime_party.live_started_at, INTERVAL :liveDurationMinutes MINUTE),
                    DATE_ADD(party.started_at, INTERVAL :startGraceMinutes MINUTE)
                ),
                realtime_party.live_ending_reason = :endingReason
            WHERE realtime_party.live_ending_started_at IS NULL
              AND COALESCE(
                    DATE_ADD(realtime_party.live_started_at, INTERVAL :liveDurationMinutes MINUTE),
                    DATE_ADD(party.started_at, INTERVAL :startGraceMinutes MINUTE)
                  ) <= :now
              AND DATE_ADD(party.started_at, INTERVAL :partyEndedAfterDays DAY) > :now
            """,
        nativeQuery = true,
    )
    fun startAutomaticRealtimeEndings(
        now: LocalDateTime,
        liveDurationMinutes: Long,
        startGraceMinutes: Long,
        partyEndedAfterDays: Long,
        endingReason: String,
    ): Int
```

- [ ] **Step 4: 서비스 호출부 수정**

`RealtimePartyEndService.kt:60-66`을 교체한다:

```kotlin
    fun startDueAutomaticEndings(now: LocalDateTime) {
        partyRepository.startAutomaticRealtimeEndings(
            now = now,
            liveDurationMinutes = RealtimeParty.LIVE_DURATION_MINUTES,
            startGraceMinutes = RealtimeParty.START_GRACE_MINUTES,
            partyEndedAfterDays = Party.ENDED_AFTER_DAYS,
            endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
        )
    }
```

`RealtimePartyEndService.kt:70-73`의 조회 범위를 넓힌다:

```kotlin
        val waitingParties =
            partyRepository.findRealtimePartiesWaitingAutomaticEnding(
                now.minusMinutes(RealtimeParty.START_GRACE_MINUTES + RealtimeParty.LIVE_DURATION_MINUTES),
            )
```

- [ ] **Step 5: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.infrastructure.persistence.PartyRepositoryTest"`
Expected: PASS

- [ ] **Step 6: 복구 조회 범위 테스트 작성**

`RealtimePartyEndServiceTest.kt`에 추가한다.
이 파일은 이미 `partyRepository: PartyRepository = mock()` 과 `service = RealtimePartyEndService(partyRepository, endingInfoPort)`
를 갖고 있고 `any` / `verify` / `whenever` import도 되어 있으므로 테스트만 추가하면 된다:

```kotlin
    @Test
    fun `복구 조회 범위는 마감선과 라이브 시간을 합친 만큼 거슬러 올라간다`() {
        val now = LocalDateTime.of(2026, 5, 24, 21, 0)
        whenever(partyRepository.findRealtimePartiesWaitingAutomaticEnding(any())).thenReturn(emptyList())
        whenever(partyRepository.findRealtimePartiesWithEndingStarted(any())).thenReturn(emptyList())

        service.findRecoverySchedules(now)

        verify(partyRepository).findRealtimePartiesWaitingAutomaticEnding(now.minusMinutes(40))
    }
```

- [ ] **Step 7: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.service.RealtimePartyEndServiceTest"`
Expected: PASS

- [ ] **Step 8: 전체 빌드 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt \
        src/main/kotlin/com/team2/server/party/application/service/RealtimePartyEndService.kt \
        src/test/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepositoryTest.kt \
        src/test/kotlin/com/team2/server/party/application/service/RealtimePartyEndServiceTest.kt
git commit -m "fix: 재기동 복구 경로에 파티 시작 마감선 반영"
```

---

### Task 4: 입장 창과 SSE 타임아웃 조정

입장 가능 종료 시각을 실제 종료 시각에 맞추고, SSE emitter 타임아웃이 최악 시나리오를 커버하게 한다.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt:76`
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/LookupPartyInviteUseCase.kt:72`
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt:117-124`
- Modify: `src/main/kotlin/com/team2/server/party/application/dto/RealtimePartyEndResults.kt:64`
- Test: `src/test/kotlin/com/team2/server/party/application/service/PartyInviteServiceTest.kt`

**Interfaces:**
- Consumes: Task 2의 `RealtimeParty.START_GRACE_MINUTES`, `RealtimeParty.effectiveEndingStartedAt()`
- Produces: 없음 (기존 시그니처 유지)

- [ ] **Step 1: 입장 창 테스트 작성**

`PartyInviteServiceTest.kt`에 추가한다. 이 파일에는 이미 `makeParty(id, ownerId, startedAt, createdAt)`,
`makeInvite(party, token, expiresAt)` 헬퍼와 `service.resolveEnterableRealtimeInvite(inviteToken, now)` 를
쓰는 테스트들이 있으므로 같은 형태를 따른다:

```kotlin
    @Test
    fun `resolveEnterableRealtimeInvite 미시작 파티는 마감선 직전까지 입장 가능`() {
        val now = LocalDateTime.now()
        val realtimeParty = makeParty(startedAt = now.minusMinutes(29))
        val invite = makeInvite(party = realtimeParty, expiresAt = now.plusDays(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val result = service.resolveEnterableRealtimeInvite("tok", now)

        assertEquals(invite, result)
    }

    @Test
    fun `resolveEnterableRealtimeInvite 늦게 시작한 파티는 시작 10분 이내면 입장 가능`() {
        val now = LocalDateTime.now()
        val realtimeParty =
            makeParty(startedAt = now.minusMinutes(20)).apply { liveStartedAt = now.minusMinutes(5) }
        val invite = makeInvite(party = realtimeParty, expiresAt = now.plusDays(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val result = service.resolveEnterableRealtimeInvite("tok", now)

        assertEquals(invite, result)
    }

    @Test
    fun `resolveEnterableRealtimeInvite 시작 10분이 지나면 입장 불가`() {
        val now = LocalDateTime.now()
        val realtimeParty =
            makeParty(startedAt = now.minusMinutes(15)).apply { liveStartedAt = now.minusMinutes(11) }
        val invite = makeInvite(party = realtimeParty, expiresAt = now.plusDays(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val e =
            assertThrows<BusinessException> {
                service.resolveEnterableRealtimeInvite("tok", now)
            }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, e.errorCode)
    }
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.service.PartyInviteServiceTest"`
Expected: FAIL — 앞의 두 테스트가 `CHAT_NOT_ACTIVE` 예외로 실패한다.
`enterableTo`가 아직 `startedAt + 10분`이라 두 케이스 모두 이미 창이 닫혀 있기 때문이다.
세 번째 테스트는 수정 전에도 통과하며, 수정 후 회귀를 막는 가드 역할을 한다.

- [ ] **Step 3: 입장 창 계산 교체**

`PartyInviteService.kt:76`:

```kotlin
        val enterableTo = realtimeParty.effectiveEndingStartedAt()
```

`enterableFrom`(`:75`)은 그대로 둔다.

- [ ] **Step 4: 테스트 실행 (통과 확인)**

Run: `./gradlew test --tests "com.team2.server.party.application.service.PartyInviteServiceTest"`
Expected: PASS

- [ ] **Step 5: 초대장 조회 종료 시각 교체**

`LookupPartyInviteUseCase.kt:72`:

```kotlin
            liveEndAt = party.effectiveEndingStartedAt(),
```

바로 아래 `:73`의 `liveDurationMinutes = RealtimeParty.LIVE_DURATION_MINUTES` 는 그대로 둔다.
이 값은 "파티는 10분 동안 진행" 안내 문구용이고 라이브 길이 자체는 여전히 10분이다.

- [ ] **Step 6: SSE emitter 타임아웃 확대**

`EnterAndSubscribeChatUseCase.kt:117-124`의 `EMITTER_TIMEOUT_MS`를 교체한다:

```kotlin
        private const val EMITTER_TIMEOUT_MS =
            (
                (
                    RealtimeParty.ENTERABLE_BEFORE_MINUTES +
                        RealtimeParty.START_GRACE_MINUTES +
                        RealtimeParty.LIVE_DURATION_MINUTES
                ) * 60 +
                    RealtimeParty.LIVE_END_COUNTDOWN_SECONDS +
                    SSE_GRACE_CLEANUP_SECONDS
            ) * 1000L
```

- [ ] **Step 7: Swagger 설명 수정**

`RealtimePartyEndResults.kt:64`:

```kotlin
    @Schema(description = "파티 시작 기준 종료 인사하기 버튼 활성화 시각", nullable = true)
```

- [ ] **Step 8: 전체 빌드 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 컨테이너 누수 확인**

Run: `docker ps -a --filter "label=org.testcontainers"`
Expected: 결과 0개

- [ ] **Step 10: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/LookupPartyInviteUseCase.kt \
        src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt \
        src/main/kotlin/com/team2/server/party/application/dto/RealtimePartyEndResults.kt \
        src/test/kotlin/com/team2/server/party/application/service/PartyInviteServiceTest.kt
git commit -m "feat: 입장 가능 창과 SSE 타임아웃에 마감선 반영"
```

---

## 완료 후

`/team-pr #247` 로 PR을 생성한다. base는 `develop`.

PR 본문에 다음 두 가지를 리뷰 포인트로 명시한다:

- 마이그레이션은 컬럼 추가만 하고 `host_entered_at`을 남긴다. 제거하는 후속 마이그레이션이 다음 릴리즈에 필요하다.
- SSE emitter 타임아웃이 약 16분에서 약 46분으로 늘어난다. 동시 커넥션 유지 시간이 3배 가까이 증가한다.
