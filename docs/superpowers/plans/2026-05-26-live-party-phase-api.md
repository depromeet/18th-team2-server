# Live Party Phase API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티의 현재 진행 Phase(ENTRY→MUSIC→CANDLE→BURST→CLOSEABLE→END)를 인메모리로 추적하고, 중간 입장자에게 현재 Phase와 타이밍을 반환하며, Phase 변경 시 모든 참여자에게 SSE 브로드캐스트하여 동일한 화면을 보도록 한다.

**Architecture:** `ConcurrentHashMap + striped lock` 인메모리 스토어(`BurstGameSessionStore` 패턴 동일)로 Phase를 추적한다. `GET /phase`로 현재 Phase+타이밍 조회, `POST /phase/advance`로 ENTRY→MUSIC·MUSIC→CANDLE 수동 전환. CANDLE→BURST는 박터뜨리기 시작 이벤트, BURST→CLOSEABLE/CLOSEABLE→END는 기존 `PartyEndScheduler`에 훅을 추가해 자동 전환한다.

**Tech Stack:** Kotlin, Spring Boot, `ConcurrentHashMap`, SSE (`SseEmitter`), Spring Application Events, MockMvc(통합 테스트)

---

## 파일 목록

| 동작 | 경로 |
|------|------|
| Create | `src/main/kotlin/com/team2/server/party/domain/vo/PartyPhase.kt` |
| Create | `src/main/kotlin/com/team2/server/party/application/port/PartyPhaseStore.kt` |
| Create | `src/main/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStore.kt` |
| Create | `src/main/kotlin/com/team2/server/party/application/dto/PartyPhaseResult.kt` |
| Create | `src/main/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCase.kt` |
| Create | `src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt` |
| Create | `src/main/kotlin/com/team2/server/party/api/PartyPhaseApi.kt` |
| Create | `src/main/kotlin/com/team2/server/party/api/PartyPhaseController.kt` |
| Create | `src/main/kotlin/com/team2/server/burstgame/application/event/BurstGameStartedEvent.kt` |
| Create | `src/main/kotlin/com/team2/server/burstgame/infrastructure/party/BurstGameStartedPartyEventPublisher.kt` |
| Modify | `src/main/kotlin/com/team2/server/party/application/event/RealtimePartyEvents.kt` |
| Modify | `src/main/kotlin/com/team2/server/party/application/port/RealtimePartyEventBroadcaster.kt` |
| Modify | `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatRealtimePartyEventBroadcaster.kt` |
| Modify | `src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcaster.kt` |
| Modify | `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt` |
| Create | `src/test/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStoreTest.kt` |
| Create | `src/test/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCaseTest.kt` |
| Create | `src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt` |
| Create | `src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt` |

---

### Task 1: PartyPhase enum + PartyPhaseStore 포트 + InMemoryPartyPhaseStore

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/domain/vo/PartyPhase.kt`
- Create: `src/main/kotlin/com/team2/server/party/application/port/PartyPhaseStore.kt`
- Create: `src/main/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStore.kt`
- Test: `src/test/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStoreTest.kt`

- [ ] **Step 1: `PartyPhase` enum 작성**

```kotlin
// src/main/kotlin/com/team2/server/party/domain/vo/PartyPhase.kt
package com.team2.server.party.domain.vo

enum class PartyPhase {
    ENTRY, MUSIC, CANDLE, BURST, CLOSEABLE, END
}
```

- [ ] **Step 2: `PartyPhaseStore` 포트 작성**

```kotlin
// src/main/kotlin/com/team2/server/party/application/port/PartyPhaseStore.kt
package com.team2.server.party.application.port

import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

interface PartyPhaseStore {
    fun getEntry(partyId: Long): PhaseEntry?

    // CAS: entries[partyId].phase == from 일 때만 to로 전환. 성공 시 true 반환.
    fun advance(partyId: Long, from: PartyPhase, to: PartyPhase, now: LocalDateTime): Boolean

    fun removeByPartyId(partyId: Long)

    fun clear()

    data class PhaseEntry(
        val phase: PartyPhase,
        val startedAt: LocalDateTime,
    )
}
```

- [ ] **Step 3: 실패 테스트 작성**

```kotlin
// src/test/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStoreTest.kt
package com.team2.server.party.infrastructure.memory

import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPartyPhaseStoreTest {
    private lateinit var store: InMemoryPartyPhaseStore
    private val now = LocalDateTime.of(2026, 5, 26, 20, 0)

    @BeforeEach
    fun setUp() {
        store = InMemoryPartyPhaseStore()
    }

    @Test
    fun `등록 전 getEntry는 null 반환`() {
        assertNull(store.getEntry(1L))
    }

    @Test
    fun `ENTRY에서 MUSIC으로 advance 성공`() {
        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        assertTrue(advanced)
        assertEquals(PartyPhase.MUSIC, store.getEntry(1L)?.phase)
        assertEquals(now, store.getEntry(1L)?.startedAt)
    }

    @Test
    fun `현재 phase가 from과 다르면 advance 실패`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now.plusSeconds(1))

        assertFalse(advanced)
        assertEquals(now, store.getEntry(1L)?.startedAt) // 변경 없음
    }

    @Test
    fun `null 상태(미등록)는 ENTRY로 간주`() {
        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        assertTrue(advanced)
    }

    @Test
    fun `removeByPartyId 후 getEntry는 null 반환`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)
        store.removeByPartyId(1L)

        assertNull(store.getEntry(1L))
    }

    @Test
    fun `서로 다른 partyId는 독립적으로 동작`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)
        store.advance(2L, PartyPhase.ENTRY, PartyPhase.MUSIC, now.plusMinutes(1))

        assertEquals(now, store.getEntry(1L)?.startedAt)
        assertEquals(now.plusMinutes(1), store.getEntry(2L)?.startedAt)
    }
}
```

- [ ] **Step 4: 테스트 실행 — FAIL 확인**

```bash
./gradlew test --tests "com.team2.server.party.infrastructure.memory.InMemoryPartyPhaseStoreTest"
```

Expected: `InMemoryPartyPhaseStore` 클래스 없음으로 컴파일 실패

- [ ] **Step 5: `InMemoryPartyPhaseStore` 구현**

```kotlin
// src/main/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStore.kt
package com.team2.server.party.infrastructure.memory

import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryPartyPhaseStore : PartyPhaseStore {
    private val entries = ConcurrentHashMap<Long, PartyPhaseStore.PhaseEntry>()
    private val locks = Array(LOCK_STRIPE_COUNT) { Any() }

    override fun getEntry(partyId: Long): PartyPhaseStore.PhaseEntry? = entries[partyId]

    override fun advance(partyId: Long, from: PartyPhase, to: PartyPhase, now: LocalDateTime): Boolean {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            val current = entries[partyId]?.phase ?: PartyPhase.ENTRY
            if (current != from) return@synchronized false
            entries[partyId] = PartyPhaseStore.PhaseEntry(phase = to, startedAt = now)
            true
        }
    }

    override fun removeByPartyId(partyId: Long) {
        entries.remove(partyId)
    }

    override fun clear() {
        entries.clear()
    }

    private fun lockFor(partyId: Long): Any = locks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    companion object {
        private const val LOCK_STRIPE_COUNT = 64
    }
}
```

- [ ] **Step 6: 테스트 재실행 — PASS 확인**

```bash
./gradlew test --tests "com.team2.server.party.infrastructure.memory.InMemoryPartyPhaseStoreTest"
```

Expected: 6개 테스트 모두 PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/domain/vo/PartyPhase.kt \
        src/main/kotlin/com/team2/server/party/application/port/PartyPhaseStore.kt \
        src/main/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStore.kt \
        src/test/kotlin/com/team2/server/party/infrastructure/memory/InMemoryPartyPhaseStoreTest.kt
git commit -m "feat: PartyPhase enum, PartyPhaseStore 포트, InMemoryPartyPhaseStore 구현"
```

---

### Task 2: PartyPhaseResult DTO + GetPartyPhaseUseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/dto/PartyPhaseResult.kt`
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCaseTest.kt`

- [ ] **Step 1: `PartyPhaseResult` DTO 작성**

```kotlin
// src/main/kotlin/com/team2/server/party/application/dto/PartyPhaseResult.kt
package com.team2.server.party.application.dto

import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

data class PartyPhaseResult(
    val partyId: Long,
    val phase: PartyPhase,
    val phaseStartedAt: LocalDateTime,
    val serverNow: LocalDateTime,
)
```

- [ ] **Step 2: 실패 테스트 작성**

```kotlin
// src/test/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCaseTest.kt
package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class GetPartyPhaseUseCaseTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val phaseStore: PartyPhaseStore = mock()
    private val fixedNow = LocalDateTime.of(2026, 5, 26, 20, 0, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = GetPartyPhaseUseCase(partyService, participantService, phaseStore, clock)

    @Test
    fun `phase 등록 전이면 ENTRY와 파티 startedAt 반환`() {
        val partyId = 1L
        val partyStartedAt = LocalDateTime.of(2026, 5, 26, 19, 55)
        val party = RealtimeParty(ownerId = 10L, startedAt = partyStartedAt)
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.getEntry(partyId)).thenReturn(null)

        val result = useCase(partyId, userId = 10L, participantToken = null)

        assertEquals(PartyPhase.ENTRY, result.phase)
        assertEquals(partyStartedAt, result.phaseStartedAt)
        assertEquals(fixedNow, result.serverNow)
    }

    @Test
    fun `phase 등록 후 해당 phase와 startedAt 반환`() {
        val partyId = 1L
        val phaseStartedAt = LocalDateTime.of(2026, 5, 26, 20, 0, 5)
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, phaseStartedAt)
        )

        val result = useCase(partyId, userId = 10L, participantToken = null)

        assertEquals(PartyPhase.MUSIC, result.phase)
        assertEquals(phaseStartedAt, result.phaseStartedAt)
    }
}
```

- [ ] **Step 3: 테스트 실행 — FAIL 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.GetPartyPhaseUseCaseTest"
```

Expected: `GetPartyPhaseUseCase` 없음으로 컴파일 실패

- [ ] **Step 4: `GetPartyPhaseUseCase` 구현**

```kotlin
// src/main/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCase.kt
package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetPartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseStore: PartyPhaseStore,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        participantService.validatePartyMember(party, userId, participantToken)
        val entry = phaseStore.getEntry(partyId)
        return PartyPhaseResult(
            partyId = partyId,
            phase = entry?.phase ?: PartyPhase.ENTRY,
            phaseStartedAt = entry?.startedAt ?: party.startedAt,
            serverNow = now,
        )
    }
}
```

- [ ] **Step 5: 테스트 재실행 — PASS 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.GetPartyPhaseUseCaseTest"
```

Expected: 2개 테스트 모두 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/application/dto/PartyPhaseResult.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCase.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/GetPartyPhaseUseCaseTest.kt
git commit -m "feat: GetPartyPhaseUseCase 구현"
```

---

### Task 3: broadcastPhaseChanged SSE + AdvancePartyPhaseUseCase

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/port/RealtimePartyEventBroadcaster.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatRealtimePartyEventBroadcaster.kt`
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt`

- [ ] **Step 1: `RealtimePartyEventBroadcaster`에 `broadcastPhaseChanged` 추가**

`RealtimePartyEventBroadcaster.kt` 파일에 다음 메서드를 추가한다:

```kotlin
fun broadcastPhaseChanged(
    partyId: Long,
    phase: PartyPhase,
    phaseStartedAt: LocalDateTime,
    serverNow: LocalDateTime,
)
```

import 추가: `import com.team2.server.party.domain.vo.PartyPhase`

- [ ] **Step 2: `ChatRealtimePartyEventBroadcaster`에 구현 추가**

`ChatRealtimePartyEventBroadcaster.kt`에 다음을 추가한다:

```kotlin
// import 추가
import com.team2.server.party.domain.vo.PartyPhase

// 기존 inner data class들 아래에 추가
override fun broadcastPhaseChanged(
    partyId: Long,
    phase: PartyPhase,
    phaseStartedAt: LocalDateTime,
    serverNow: LocalDateTime,
) {
    sseEmitterRegistry.broadcast(
        partyId,
        SseEmitter
            .event()
            .name("party-phase-changed")
            .data(PhaseChangedPayload(partyId, phase, phaseStartedAt, serverNow))
            .build(),
    )
}

data class PhaseChangedPayload(
    val partyId: Long,
    val phase: PartyPhase,
    val phaseStartedAt: LocalDateTime,
    val serverNow: LocalDateTime,
)
```

- [ ] **Step 3: 실패 테스트 작성**

```kotlin
// src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt
package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdvancePartyPhaseUseCaseTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val phaseStore: PartyPhaseStore = mock()
    private val eventBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val fixedNow = LocalDateTime.of(2026, 5, 26, 20, 0, 5)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = AdvancePartyPhaseUseCase(partyService, participantService, phaseStore, eventBroadcaster, clock)

    @Test
    fun `호스트가 ENTRY→MUSIC 전환 성공 시 SSE 브로드캐스트`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.advance(eq(partyId), eq(PartyPhase.ENTRY), eq(PartyPhase.MUSIC), any())).thenReturn(true)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow)
        )

        val result = useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        assertEquals(PartyPhase.MUSIC, result.phase)
        verify(eventBroadcaster).broadcastPhaseChanged(eq(partyId), eq(PartyPhase.MUSIC), any(), any())
    }

    @Test
    fun `비호스트가 ENTRY→MUSIC 시도 시 403`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)

        assertFailsWith<BusinessException> {
            useCase(partyId, userId = 99L, participantToken = null, currentPhase = PartyPhase.ENTRY)
        }
        verify(phaseStore, never()).advance(any(), any(), any(), any())
    }

    @Test
    fun `CAS 실패 시 SSE 브로드캐스트 없음`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.advance(any(), any(), any(), any())).thenReturn(false)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow.minusSeconds(3))
        )

        useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        verify(eventBroadcaster, never()).broadcastPhaseChanged(any(), any(), any(), any())
    }

    @Test
    fun `허용되지 않는 currentPhase 시 400`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)

        assertFailsWith<BusinessException> {
            useCase(partyId, userId = 10L, participantToken = null, currentPhase = PartyPhase.BURST)
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — FAIL 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.AdvancePartyPhaseUseCaseTest"
```

Expected: `AdvancePartyPhaseUseCase` 없음으로 컴파일 실패

- [ ] **Step 5: `AdvancePartyPhaseUseCase` 구현**

```kotlin
// src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt
package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class AdvancePartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseStore: PartyPhaseStore,
    private val eventBroadcaster: RealtimePartyEventBroadcaster,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        currentPhase: PartyPhase,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        val nextPhase = ALLOWED_TRANSITIONS[currentPhase]
            ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        when (currentPhase) {
            PartyPhase.ENTRY -> {
                if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            PartyPhase.MUSIC -> participantService.validatePartyMember(party, userId, participantToken)
            else -> throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val advanced = phaseStore.advance(partyId, currentPhase, nextPhase, now)
        val entry = phaseStore.getEntry(partyId)
        val phase = entry?.phase ?: PartyPhase.ENTRY
        val phaseStartedAt = entry?.startedAt ?: party.startedAt

        if (advanced) {
            eventBroadcaster.broadcastPhaseChanged(partyId, phase, phaseStartedAt, now)
        }

        return PartyPhaseResult(
            partyId = partyId,
            phase = phase,
            phaseStartedAt = phaseStartedAt,
            serverNow = now,
        )
    }

    companion object {
        val ALLOWED_TRANSITIONS = mapOf(
            PartyPhase.ENTRY to PartyPhase.MUSIC,
            PartyPhase.MUSIC to PartyPhase.CANDLE,
        )
    }
}
```

- [ ] **Step 6: 테스트 재실행 — PASS 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.AdvancePartyPhaseUseCaseTest"
```

Expected: 4개 테스트 모두 PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/application/port/RealtimePartyEventBroadcaster.kt \
        src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatRealtimePartyEventBroadcaster.kt \
        src/main/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCase.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/AdvancePartyPhaseUseCaseTest.kt
git commit -m "feat: AdvancePartyPhaseUseCase, broadcastPhaseChanged SSE 구현"
```

---

### Task 4: PartyPhaseController + API

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/PartyPhaseApi.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/PartyPhaseController.kt`

- [ ] **Step 1: `PartyPhaseApi` 인터페이스 작성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/PartyPhaseApi.kt
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.domain.vo.PartyPhase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Party Phase", description = "실시간 파티 Phase API")
interface PartyPhaseApi {
    @Operation(summary = "현재 Phase 조회", description = "중간 입장 시 현재 Phase와 시작 시각, 서버 현재 시각 반환")
    fun getPhase(
        principal: UserPrincipal?,
        participantToken: String?,
        partyId: Long,
    ): ApiResponse<PartyPhaseResult>

    @Operation(
        summary = "Phase 전환",
        description = "ENTRY→MUSIC: 호스트 전용. MUSIC→CANDLE: 모든 파티 멤버. currentPhase가 이미 변경된 경우 현재 phase 그대로 반환.",
    )
    fun advancePhase(
        principal: UserPrincipal?,
        participantToken: String?,
        partyId: Long,
        request: AdvancePartyPhaseRequest,
    ): ApiResponse<PartyPhaseResult>
}

data class AdvancePartyPhaseRequest(val currentPhase: PartyPhase)
```

- [ ] **Step 2: `PartyPhaseController` 작성**

```kotlin
// src/main/kotlin/com/team2/server/party/api/PartyPhaseController.kt
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.usecase.AdvancePartyPhaseUseCase
import com.team2.server.party.application.usecase.GetPartyPhaseUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyPhaseController(
    private val getPartyPhaseUseCase: GetPartyPhaseUseCase,
    private val advancePartyPhaseUseCase: AdvancePartyPhaseUseCase,
) : PartyPhaseApi {
    @GetMapping("/{partyId}/phase")
    override fun getPhase(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
    ): ApiResponse<PartyPhaseResult> =
        ApiResponse.success(
            HttpStatus.OK,
            getPartyPhaseUseCase(partyId, principal?.userId, participantToken),
        )

    @PostMapping("/{partyId}/phase/advance")
    override fun advancePhase(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
        @RequestBody request: AdvancePartyPhaseRequest,
    ): ApiResponse<PartyPhaseResult> =
        ApiResponse.success(
            HttpStatus.OK,
            advancePartyPhaseUseCase(partyId, principal?.userId, participantToken, request.currentPhase),
        )
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/api/PartyPhaseApi.kt \
        src/main/kotlin/com/team2/server/party/api/PartyPhaseController.kt
git commit -m "feat: PartyPhaseController GET/POST 엔드포인트 추가"
```

---

### Task 5: CANDLE→BURST 자동 전환 (BurstGame 시작 이벤트 연동)

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/event/BurstGameStartedEvent.kt`
- Modify: `src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcaster.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/party/BurstGameStartedPartyEventPublisher.kt`
- Modify: `src/main/kotlin/com/team2/server/party/application/event/RealtimePartyEvents.kt`
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt`

- [ ] **Step 1: `BurstGameStartedEvent` 데이터 클래스 추가**

```kotlin
// src/main/kotlin/com/team2/server/burstgame/application/event/BurstGameStartedEvent.kt
package com.team2.server.burstgame.application.event

import java.time.LocalDateTime

data class BurstGameStartedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)
```

- [ ] **Step 2: `SseBurstGameEventBroadcaster.broadcastStarted`에서 이벤트 발행**

`SseBurstGameEventBroadcaster.kt`의 `broadcastStarted` 메서드를 다음과 같이 수정한다:

```kotlin
override fun broadcastStarted(snapshot: BurstGameSnapshot) {
    emit(snapshot.partyId, EVENT_STARTED, BurstGameStartedPayload.from(snapshot))
    applicationEventPublisher.publishEvent(BurstGameStartedEvent(snapshot.partyId, snapshot.serverTime))
}
```

- [ ] **Step 3: `RealtimePartyBurstGameStartedEvent` 추가**

`RealtimePartyEvents.kt` 파일 끝에 다음을 추가한다:

```kotlin
data class RealtimePartyBurstGameStartedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)
```

- [ ] **Step 4: `BurstGameStartedPartyEventPublisher` 작성**

```kotlin
// src/main/kotlin/com/team2/server/burstgame/infrastructure/party/BurstGameStartedPartyEventPublisher.kt
package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.event.BurstGameStartedEvent
import com.team2.server.party.application.event.RealtimePartyBurstGameStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BurstGameStartedPartyEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameStarted(event: BurstGameStartedEvent) {
        applicationEventPublisher.publishEvent(
            RealtimePartyBurstGameStartedEvent(partyId = event.partyId, startedAt = event.startedAt),
        )
    }
}
```

- [ ] **Step 5: `PartyEndScheduler`에 `PartyPhaseStore` 주입 + CANDLE→BURST 리스너 추가**

`PartyEndScheduler.kt`의 생성자에 `PartyPhaseStore` 추가:

```kotlin
// 기존 생성자에 추가
private val phaseStore: PartyPhaseStore,
```

`import` 추가:
```kotlin
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.event.RealtimePartyBurstGameStartedEvent
import com.team2.server.party.domain.vo.PartyPhase
```

기존 `onBurstGameEnded` 바로 위에 새 리스너 추가:

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
fun onBurstGameStarted(event: RealtimePartyBurstGameStartedEvent) {
    val advanced = phaseStore.advance(event.partyId, PartyPhase.CANDLE, PartyPhase.BURST, event.startedAt)
    if (advanced) {
        realtimePartyEventBroadcaster.broadcastPhaseChanged(
            event.partyId, PartyPhase.BURST, event.startedAt, event.startedAt,
        )
    }
}
```

- [ ] **Step 6: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/team2/server/burstgame/application/event/BurstGameStartedEvent.kt \
        src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcaster.kt \
        src/main/kotlin/com/team2/server/burstgame/infrastructure/party/BurstGameStartedPartyEventPublisher.kt \
        src/main/kotlin/com/team2/server/party/application/event/RealtimePartyEvents.kt \
        src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt
git commit -m "feat: CANDLE→BURST 자동 Phase 전환 연동"
```

---

### Task 6: BURST→CLOSEABLE, CLOSEABLE→END 자동 전환

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt`

- [ ] **Step 1: `onBurstGameEnded`에 BURST→CLOSEABLE 전환 추가**

`PartyEndScheduler.kt`의 `onBurstGameEnded` 메서드를 다음과 같이 수정한다:

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
fun onBurstGameEnded(event: RealtimePartyBurstGameEndedEvent) {
    if (handleBurstGameEndedUseCase(event.partyId)) {
        sendHostEndAvailableIfNeeded(event.partyId, event.endedAt)
    }
    val advanced = phaseStore.advance(event.partyId, PartyPhase.BURST, PartyPhase.CLOSEABLE, event.endedAt)
    if (advanced) {
        realtimePartyEventBroadcaster.broadcastPhaseChanged(
            event.partyId, PartyPhase.CLOSEABLE, event.endedAt, event.endedAt,
        )
    }
}
```

- [ ] **Step 2: `onRealtimePartyEndingStarted`에 CLOSEABLE→END 전환 추가**

`PartyEndScheduler.kt`의 `onRealtimePartyEndingStarted` 메서드를 다음과 같이 수정한다:

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onRealtimePartyEndingStarted(event: RealtimePartyEndingStartedEvent) {
    scheduleEndingEvents(
        RealtimeEndingScheduleTarget(
            partyId = event.partyId,
            endingStartedAt = event.endingStartedAt,
            endedAt = event.endedAt,
        ),
        emitEnding = true,
    )
    val advanced = phaseStore.advance(event.partyId, PartyPhase.CLOSEABLE, PartyPhase.END, event.endingStartedAt)
    if (advanced) {
        realtimePartyEventBroadcaster.broadcastPhaseChanged(
            event.partyId, PartyPhase.END, event.endingStartedAt, event.endingStartedAt,
        )
    }
}
```

- [ ] **Step 3: `sendPartyEnded`의 cleanup 태스크에 phase store 정리 추가**

`sendPartyEnded` 메서드 내 `taskScheduler.schedule` 람다를 다음과 같이 수정한다:

```kotlin
taskScheduler.schedule(
    {
        realtimePartyEventBroadcaster.completeParty(target.partyId)
        phaseStore.removeByPartyId(target.partyId)
        partyStates.remove(target.partyId, state)
    },
    Instant
        .now(clock)
        .plusSeconds(GRACE_CLEANUP_SECONDS),
)
```

- [ ] **Step 4: 전체 빌드 + 테스트 실행**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, 모든 기존 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt
git commit -m "feat: BURST→CLOSEABLE, CLOSEABLE→END 자동 Phase 전환 및 파티 종료 시 phase store 정리"
```

---

### Task 7: 컨트롤러 통합 테스트

**Files:**
- Test: `src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt`

- [ ] **Step 1: 통합 테스트 작성**

```kotlin
// src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt
package com.team2.server.party.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PartyPhaseControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val userRepository: UserRepository,
        private val characterRepository: CharacterRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val phaseStore: PartyPhaseStore,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private val objectMapper = ObjectMapper()

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            phaseStore.clear()
        }

        @AfterEach
        fun tearDown() {
            phaseStore.clear()
        }

        @Test
        fun `호스트가 GET phase 조회 시 ENTRY 반환`() {
            val fixture = saveHostAndParty()

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/phase") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("ENTRY") }
                    jsonPath("$.data.phaseStartedAt") { exists() }
                    jsonPath("$.data.serverNow") { exists() }
                }
        }

        @Test
        fun `호스트가 ENTRY→MUSIC advance 성공`() {
            val fixture = saveHostAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(AdvancePartyPhaseRequest(PartyPhase.ENTRY))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("MUSIC") }
                }
        }

        @Test
        fun `참여자 token으로 GET phase 조회 성공`() {
            val fixture = saveParticipantAndParty()
            phaseStore.advance(fixture.partyId, PartyPhase.ENTRY, PartyPhase.MUSIC, LocalDateTime.now())

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/phase") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("MUSIC") }
                }
        }

        @Test
        fun `비호스트가 ENTRY→MUSIC advance 시 403`() {
            val fixture = saveParticipantAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("X-Participant-Token", fixture.participantToken)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(AdvancePartyPhaseRequest(PartyPhase.ENTRY))
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `허용되지 않는 currentPhase 시 400`() {
            val fixture = saveHostAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(AdvancePartyPhaseRequest(PartyPhase.BURST))
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        // ── fixtures ─────────────────────────────────────────────

        private data class HostFixture(val partyId: Long, val hostToken: String)
        private data class ParticipantFixture(val partyId: Long, val participantToken: String)

        private fun saveHostAndParty(): HostFixture {
            val character = characterRepository.save(Character(name = "C"))
            val host = userRepository.save(User(provider = AuthProvider.KAKAO, providerId = "host-1"))
            val party = partyRepository.save(
                RealtimeParty(ownerId = host.id, startedAt = LocalDateTime.now().minusMinutes(1))
            )
            val hostToken = tokenProvider.createAccessToken(host.id)
            return HostFixture(partyId = party.id, hostToken = hostToken)
        }

        private fun saveParticipantAndParty(): ParticipantFixture {
            val character = characterRepository.save(Character(name = "C"))
            val host = userRepository.save(User(provider = AuthProvider.KAKAO, providerId = "host-2"))
            val party = partyRepository.save(
                RealtimeParty(ownerId = host.id, startedAt = LocalDateTime.now().minusMinutes(1))
            )
            val guest = userRepository.save(User(provider = AuthProvider.KAKAO, providerId = "guest-1"))
            val participant = participantRepository.save(Participant(party = party, user = guest))
            val invite = partyInviteRepository.save(
                PartyInvite(party = party, token = "test-token", expiresAt = LocalDateTime.now().plusHours(1))
            )
            val profile = profileRepository.save(
                RealtimeParticipantProfile(
                    participant = participant,
                    nickname = "게스트",
                    character = character,
                    participantToken = "pt-guest-1",
                )
            )
            return ParticipantFixture(partyId = party.id, participantToken = profile.participantToken)
        }
    }
```

- [ ] **Step 2: 테스트 실행 — PASS 확인**

```bash
./gradlew test --tests "com.team2.server.party.api.PartyPhaseControllerTest"
```

Expected: 5개 테스트 모두 PASS

- [ ] **Step 3: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/api/PartyPhaseControllerTest.kt
git commit -m "test: PartyPhaseController 통합 테스트 추가"
```

---

## Self-Review

**Spec coverage 체크:**

| 요구사항 | Task |
|----------|------|
| Phase enum (ENTRY→MUSIC→CANDLE→BURST→CLOSEABLE→END) | Task 1 |
| InMemory store (ConcurrentHashMap + striped lock) | Task 1 |
| GET /phase — phase + phaseStartedAt + serverNow 반환 | Task 2, 4 |
| POST /phase/advance — ENTRY→MUSIC (호스트), MUSIC→CANDLE (전체) | Task 3, 4 |
| CAS로 중복 advance 방지 | Task 1, 3 |
| SSE broadcastPhaseChanged | Task 3 |
| CANDLE→BURST 자동 전환 (BurstGame 시작 이벤트) | Task 5 |
| BURST→CLOSEABLE 자동 전환 | Task 6 |
| CLOSEABLE→END 자동 전환 | Task 6 |
| 파티 종료 시 phase store cleanup | Task 6 |
| 컨트롤러 통합 테스트 | Task 7 |
