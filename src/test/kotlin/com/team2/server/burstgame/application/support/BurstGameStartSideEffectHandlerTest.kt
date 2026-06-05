package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.dto.BurstGameStartResult
import com.team2.server.burstgame.application.port.BurstGameEndScheduler
import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class BurstGameStartSideEffectHandlerTest {
    private val sessionService: BurstGameSessionService = mock()
    private val eventBroadcaster: BurstGameEventBroadcaster = mock()
    private val endScheduler: BurstGameEndScheduler = mock()
    private val candleBlowSessionStore: CandleBlowSessionStore = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-21T11:10:00Z"), ZoneId.of("Asia/Seoul"))
    private val handler =
        BurstGameStartSideEffectHandler(
            sessionService = sessionService,
            eventBroadcaster = eventBroadcaster,
            endScheduler = endScheduler,
            candleBlowSessionStore = candleBlowSessionStore,
            clock = clock,
        )

    @Test
    fun `시작 성공 후 종료 스케줄과 started 이벤트와 촛불 세션 정리를 실행한다`() {
        val snapshot = activeSnapshot()
        val result = BurstGameStartResult.Started(snapshot, created = true)

        executeAfterCommit {
            handler.completeStartedAfterCommit(partyId = 1L, result = result, now = snapshot.startedAt)
        }

        verify(endScheduler).schedule(eq(1L), eq(snapshot.endsAt), any())
        verify(eventBroadcaster).broadcastStarted(snapshot)
        verify(candleBlowSessionStore).removeByPartyId(1L)
    }

    @Test
    fun `트랜잭션 동기화가 없으면 시작 후처리 등록을 거부한다`() {
        val snapshot = activeSnapshot()
        val result = BurstGameStartResult.Started(snapshot, created = true)

        assertThrows<IllegalStateException> {
            handler.completeStartedAfterCommit(partyId = 1L, result = result, now = snapshot.startedAt)
        }

        verify(endScheduler, never()).schedule(eq(1L), eq(snapshot.endsAt), any())
        verify(eventBroadcaster, never()).broadcastStarted(snapshot)
    }

    @Test
    fun `트랜잭션 동기화가 활성화되어 있으면 시작 후처리를 커밋 이후로 지연한다`() {
        val snapshot = activeSnapshot()
        val result = BurstGameStartResult.Started(snapshot, created = true)

        registerAfterCommit {
            handler.completeStartedAfterCommit(partyId = 1L, result = result, now = snapshot.startedAt)

            verify(endScheduler, never()).schedule(eq(1L), eq(snapshot.endsAt), any())
            verify(eventBroadcaster, never()).broadcastStarted(snapshot)
            verify(candleBlowSessionStore, never()).removeByPartyId(1L)

            val synchronizations = TransactionSynchronizationManager.getSynchronizations()
            assertEquals(1, synchronizations.size)
            synchronizations.forEach { it.afterCommit() }

            verify(endScheduler).schedule(eq(1L), eq(snapshot.endsAt), any())
            verify(eventBroadcaster).broadcastStarted(snapshot)
            verify(candleBlowSessionStore).removeByPartyId(1L)
        }
    }

    @Test
    fun `촛불 세션 정리 실패는 시작 성공 흐름으로 전파하지 않는다`() {
        val snapshot = activeSnapshot()
        val result = BurstGameStartResult.Started(snapshot, created = true)
        whenever(candleBlowSessionStore.removeByPartyId(1L)).thenThrow(IllegalStateException("cleanup failed"))

        assertDoesNotThrow {
            executeAfterCommit {
                handler.completeStartedAfterCommit(partyId = 1L, result = result, now = snapshot.startedAt)
            }
        }

        verify(endScheduler).schedule(eq(1L), eq(snapshot.endsAt), any())
        verify(eventBroadcaster).broadcastStarted(snapshot)
    }

    @Test
    fun `started 이벤트 실패 시 rollback 실패보다 원래 예외를 전파한다`() {
        val snapshot = activeSnapshot()
        val result = BurstGameStartResult.Started(snapshot, created = true)
        val broadcastException = IllegalStateException("broadcast failed")
        whenever(eventBroadcaster.broadcastStarted(snapshot)).thenThrow(broadcastException)
        whenever(endScheduler.cancel(1L)).thenThrow(IllegalStateException("cancel failed"))

        val ex =
            assertThrows<IllegalStateException> {
                executeAfterCommit {
                    handler.completeStartedAfterCommit(partyId = 1L, result = result, now = snapshot.startedAt)
                }
            }

        assertEquals(broadcastException, ex)
    }

    @Test
    fun `이미 종료된 start 결과는 ended 이벤트 발행 후 예외로 변환한다`() {
        val snapshot = endedSnapshot()
        val result = BurstGameStartResult.AlreadyEnded(snapshot, endedNow = true)

        val ex =
            assertThrows<BusinessException> {
                handler.resolve(result)
            }

        assertEquals(ErrorCode.BURST_GAME_ALREADY_ENDED, ex.errorCode)
        verify(eventBroadcaster).broadcastEnded(snapshot)
        verify(candleBlowSessionStore, never()).removeByPartyId(1L)
    }

    private fun activeSnapshot(): BurstGameSnapshot {
        val startedAt = LocalDateTime.of(2026, 5, 21, 20, 10)
        return BurstGameSnapshot(
            partyId = 1L,
            myParticipantId = 10L,
            status = BurstGameRoundStatus.ACTIVE,
            startedAt = startedAt,
            endsAt = startedAt.plusSeconds(20),
            totalTapCount = 0,
            myTapCount = 0,
            colorChanged = false,
            stateVersion = 0,
            serverTime = startedAt,
            remainingSeconds = 20,
            rankings = emptyList(),
        )
    }

    private fun endedSnapshot(): BurstGameSnapshot {
        val startedAt = LocalDateTime.of(2026, 5, 21, 20, 10)
        return activeSnapshot().copy(
            status = BurstGameRoundStatus.ENDED,
            serverTime = startedAt.plusSeconds(20),
            remainingSeconds = 0,
        )
    }

    private fun executeAfterCommit(block: () -> Unit) {
        registerAfterCommit {
            block()
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        }
    }

    private fun registerAfterCommit(block: () -> Unit) {
        TransactionSynchronizationManager.initSynchronization()
        try {
            block()
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }
}
