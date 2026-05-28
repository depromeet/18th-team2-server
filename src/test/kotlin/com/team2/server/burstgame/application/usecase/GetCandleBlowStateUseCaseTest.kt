package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver.ResolvedRealtimeParticipant
import com.team2.server.burstgame.application.support.CandleBlowEndEventPublisher
import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.infrastructure.candle.InMemoryCandleBlowSessionStore
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
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
class GetCandleBlowStateUseCaseTest {
    private val participantResolver: BurstGameParticipantResolver = mock()
    private val sessionStore = InMemoryCandleBlowSessionStore()
    private val eventBroadcaster: CandleBlowEventBroadcaster = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-21T11:02:00Z"), ZoneId.of("Asia/Seoul"))
    private val useCase =
        GetCandleBlowStateUseCase(
            participantResolver = participantResolver,
            sessionStore = sessionStore,
            endEventPublisher = CandleBlowEndEventPublisher(eventBroadcaster),
            clock = clock,
        )

    @Test
    fun `조회로 촛불끄기 timeout 종료가 발생하면 ended 이벤트를 커밋 이후 발행한다`() {
        whenever(participantResolver.resolveWithParty(1L, null, "tok"))
            .thenReturn(
                resolved(
                    partyStartedAt =
                        LocalDateTime
                            .now(clock)
                            .minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + CandleBlowPolicy.DURATION_SECONDS),
                ),
            )

        TransactionSynchronizationManager.initSynchronization()
        try {
            val response = useCase(partyId = 1L, userId = null, participantToken = "tok")

            assertEquals(CandleBlowFinishedReason.TIMEOUT, response.finishedReason)
            verify(eventBroadcaster, never()).broadcastEnded(any())

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            verify(eventBroadcaster).broadcastEnded(any())
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun resolved(partyStartedAt: LocalDateTime): ResolvedRealtimeParticipant =
        ResolvedRealtimeParticipant(
            party =
                RealtimeParty(
                    ownerId = 1L,
                    name = "실시간 파티",
                    celebrantNickname = "주인공",
                    startedAt = partyStartedAt,
                ),
            participant =
                BurstGameParticipantInfo(
                    participantId = 10L,
                    nickname = "player",
                    characterId = null,
                    characterImageUrl = null,
                    role = "PARTICIPANT",
                ),
        )
}
