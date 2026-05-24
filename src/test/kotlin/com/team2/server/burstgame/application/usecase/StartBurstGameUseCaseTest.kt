package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.BurstGameEndScheduler
import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class StartBurstGameUseCaseTest {
    private val participantResolver: BurstGameParticipantResolver = mock()
    private val sessionService: BurstGameSessionService = mock()
    private val eventBroadcaster: BurstGameEventBroadcaster = mock()
    private val endScheduler: BurstGameEndScheduler = mock()
    private val candleBlowSessionStore: CandleBlowSessionStore = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-21T11:10:00Z"), ZoneId.of("Asia/Seoul"))
    private val useCase =
        StartBurstGameUseCase(
            participantResolver = participantResolver,
            sessionService = sessionService,
            eventBroadcaster = eventBroadcaster,
            endScheduler = endScheduler,
            candleBlowSessionStore = candleBlowSessionStore,
            clock = clock,
        )

    @Test
    fun `박터뜨리기 시작 성공 후 촛불끄기 세션을 제거한다`() {
        val participant = participant(10L)
        val snapshot = activeSnapshot()
        whenever(participantResolver.resolve(1L, null, "tok")).thenReturn(participant)
        whenever(sessionService.start(eq(1L), eq(participant), any()))
            .thenReturn(BurstGameSessionService.StartResult.Started(snapshot, created = true))

        val response = useCase(partyId = 1L, userId = null, participantToken = "tok")

        assertEquals(1L, response.partyId)
        verify(endScheduler).schedule(eq(1L), eq(snapshot.endsAt), any())
        verify(eventBroadcaster).broadcastStarted(snapshot)
        verify(candleBlowSessionStore).removeByPartyId(1L)
    }

    @Test
    fun `start 재시도에서 lazy 종료가 발생하면 종료 이벤트를 발행한 뒤 이미 종료 예외를 던진다`() {
        val participant = participant(10L)
        val snapshot = endedSnapshot()
        whenever(participantResolver.resolve(1L, null, "tok")).thenReturn(participant)
        whenever(sessionService.start(eq(1L), eq(participant), any()))
            .thenReturn(BurstGameSessionService.StartResult.AlreadyEnded(snapshot, endedNow = true))

        val ex =
            assertThrows<BusinessException> {
                useCase(partyId = 1L, userId = null, participantToken = "tok")
            }

        assertEquals(ErrorCode.BURST_GAME_ALREADY_ENDED, ex.errorCode)
        verify(eventBroadcaster).broadcastEnded(snapshot)
        verify(candleBlowSessionStore, never()).removeByPartyId(1L)
    }

    private fun participant(participantId: Long): BurstGameParticipantInfo =
        BurstGameParticipantInfo(
            participantId = participantId,
            nickname = "player",
            characterId = null,
            characterImageUrl = null,
            role = "PARTICIPANT",
        )

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
            winners = emptyList(),
        )
    }

    private fun endedSnapshot(): BurstGameSnapshot {
        val startedAt = LocalDateTime.of(2026, 5, 21, 20, 10)
        return BurstGameSnapshot(
            partyId = 1L,
            myParticipantId = 10L,
            status = BurstGameRoundStatus.ENDED,
            startedAt = startedAt,
            endsAt = startedAt.plusSeconds(20),
            totalTapCount = 0,
            myTapCount = 0,
            colorChanged = false,
            stateVersion = 1,
            serverTime = startedAt.plusSeconds(20),
            remainingSeconds = 0,
            rankings = emptyList(),
            winners = emptyList(),
        )
    }
}
