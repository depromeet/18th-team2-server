package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver.ResolvedRealtimeParticipant
import com.team2.server.burstgame.application.support.BurstGameStartSideEffectHandler
import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.RealtimeParty
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
    private val partyStartedAt = LocalDateTime.of(2026, 5, 21, 20, 9)
    private val participantResolver: BurstGameParticipantResolver = mock()
    private val sessionService: BurstGameSessionService = mock()
    private val startSideEffectHandler: BurstGameStartSideEffectHandler = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-21T11:10:00Z"), ZoneId.of("Asia/Seoul"))
    private val useCase =
        StartBurstGameUseCase(
            participantResolver = participantResolver,
            sessionService = sessionService,
            startSideEffectHandler = startSideEffectHandler,
            clock = clock,
        )

    @Test
    fun `박터뜨리기 시작 성공 후 후처리를 실행한다`() {
        val participant = participant(10L)
        val snapshot = activeSnapshot()
        val startResult = BurstGameSessionService.StartResult.Started(snapshot, created = true)
        val now = LocalDateTime.ofInstant(clock.instant(), clock.zone)
        whenever(participantResolver.resolveWithParty(1L, null, "tok")).thenReturn(resolved(participant))
        whenever(sessionService.start(eq(1L), eq(partyStartedAt), eq(participant), any()))
            .thenReturn(startResult)
        whenever(startSideEffectHandler.resolve(startResult)).thenReturn(startResult)

        val response = useCase(partyId = 1L, userId = null, participantToken = "tok")

        assertEquals(1L, response.partyId)
        verify(startSideEffectHandler).completeStartedAfterCommit(1L, startResult, now)
    }

    @Test
    fun `start 재시도에서 lazy 종료가 발생하면 후처리 예외를 전파한다`() {
        val participant = participant(10L)
        val snapshot = endedSnapshot()
        val startResult = BurstGameSessionService.StartResult.AlreadyEnded(snapshot, endedNow = true)
        whenever(participantResolver.resolveWithParty(1L, null, "tok")).thenReturn(resolved(participant))
        whenever(sessionService.start(eq(1L), eq(partyStartedAt), eq(participant), any()))
            .thenReturn(startResult)
        whenever(startSideEffectHandler.resolve(startResult))
            .thenThrow(BusinessException(ErrorCode.BURST_GAME_ALREADY_ENDED))

        val ex =
            assertThrows<BusinessException> {
                useCase(partyId = 1L, userId = null, participantToken = "tok")
            }

        assertEquals(ErrorCode.BURST_GAME_ALREADY_ENDED, ex.errorCode)
        verify(startSideEffectHandler, never()).completeStartedAfterCommit(eq(1L), any(), any())
    }

    private fun resolved(participant: BurstGameParticipantInfo): ResolvedRealtimeParticipant =
        ResolvedRealtimeParticipant(
            party =
                RealtimeParty(
                    ownerId = 1L,
                    name = "실시간 파티",
                    celebrantNickname = "주인공",
                    startedAt = partyStartedAt,
                ),
            participant = participant,
        )

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
