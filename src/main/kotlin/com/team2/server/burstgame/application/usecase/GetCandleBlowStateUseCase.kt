package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowStateResponse
import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetCandleBlowStateUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val eventBroadcaster: CandleBlowEventBroadcaster,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): CandleBlowStateResponse {
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
            val wasFinished = session.isFinished()
            val snapshot = session.snapshot(now)
            if (!wasFinished && snapshot.finishedReason != null) {
                runCatching {
                    eventBroadcaster.broadcastEnded(snapshot)
                }.onFailure { ex ->
                    log.error("Failed to broadcast candle blow end after state lookup. partyId={}", partyId, ex)
                }
            }
            CandleBlowStateResponse.from(snapshot)
        }
    }
}
