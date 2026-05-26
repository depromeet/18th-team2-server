package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.CandleBlowUpdateEventPublisher
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class BlowCandleUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val updateEventPublisher: CandleBlowUpdateEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        candleId: Int,
        userId: Long?,
        participantToken: String?,
    ): CandleBlowResponse {
        val context = participantResolver.resolveWithParty(partyId, userId, participantToken)
        val now = LocalDateTime.now(clock)
        return sessionStore.getOrCreateWithLock(
            partyId = partyId,
            sessionFactory = {
                CandleBlowSession.fromPartyStartedAt(
                    partyId = partyId,
                    partyStartedAt = context.party.startedAt,
                )
            },
        ) { session, _ ->
            val result = session.blow(candleId, now)
            updateEventPublisher.publish(
                changed = result.changed,
                shouldBroadcastEnded =
                    result.finishedNow &&
                        session.markAndCheckBroadcastNeeded(CandleBlowStatus.FINISHED),
                snapshot = result.snapshot,
            )
            CandleBlowResponse.from(result.snapshot)
        }
    }
}
