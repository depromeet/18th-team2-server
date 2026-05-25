package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.CandleBlowEndEventPublisher
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetCandleBlowStateUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val endEventPublisher: CandleBlowEndEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): CandleBlowResponse {
        val context = participantResolver.resolveWithParty(partyId, userId, participantToken)
        val now = LocalDateTime.now(clock)
        val result =
            sessionStore.getOrCreateWithLock(
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
                CandleBlowStateLookupResult(
                    response = CandleBlowResponse.from(snapshot),
                    endedSnapshot = snapshot.takeIf { !wasFinished && it.finishedReason != null },
                )
            }
        result.endedSnapshot?.let(endEventPublisher::publishEndedAfterCommit)
        return result.response
    }

    private data class CandleBlowStateLookupResult(
        val response: CandleBlowResponse,
        val endedSnapshot: CandleBlowSnapshot?,
    )
}
