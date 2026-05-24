package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.BlowCandleResponse
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class BlowCandleUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        candleId: Int,
        userId: Long?,
        participantToken: String?,
    ): BlowCandleResponse {
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
            BlowCandleResponse.from(session.blow(candleId, now).snapshot)
        }
    }
}
