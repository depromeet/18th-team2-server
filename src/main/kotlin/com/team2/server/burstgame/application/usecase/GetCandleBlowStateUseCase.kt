package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.dto.CandleBlowStateLookupResult
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.CandleBlowEndEventPublisher
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class GetCandleBlowStateUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val endEventPublisher: CandleBlowEndEventPublisher,
    private val candleBlowProperties: CandleBlowProperties,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): CandleBlowResponse {
        val context = participantResolver.resolveWithParty(partyId, userId, participantToken)
        val now = java.time.LocalDateTime.now(clock)
        val hostEnteredAt =
            context.party.hostEnteredAt ?: return CandleBlowResponse.from(CandleBlowSnapshot.waiting(partyId))
        val result =
            sessionStore.getOrCreateWithLock(
                partyId = partyId,
                sessionFactory = {
                    CandleBlowSession.fromHostEnteredAt(
                        partyId = partyId,
                        hostEnteredAt = hostEnteredAt,
                        durationSeconds = candleBlowProperties.durationSeconds,
                    )
                },
            ) { session, _ ->
                val wasFinished = session.isFinished()
                val snapshot = session.snapshot(now)
                CandleBlowStateLookupResult(
                    response = CandleBlowResponse.from(snapshot),
                    endedSnapshot =
                        snapshot.takeIf {
                            !wasFinished &&
                                it.finishedReason != null &&
                                session.markAndCheckBroadcastNeeded(CandleBlowStatus.FINISHED)
                        },
                )
            }
        result.endedSnapshot?.let(endEventPublisher::publishEndedAfterCommit)
        return result.response
    }
}
