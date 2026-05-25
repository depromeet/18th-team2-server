package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class InMemoryCandleBlowStatusReader(
    private val sessionStore: CandleBlowSessionStore,
) : CandleBlowStatusReader {
    override fun isCandleBlowFinished(
        partyId: Long,
        partyStartedAt: LocalDateTime,
        now: LocalDateTime,
    ): Boolean =
        sessionStore.getOrCreateWithLock(
            partyId = partyId,
            sessionFactory = {
                CandleBlowSession.fromPartyStartedAt(
                    partyId = partyId,
                    partyStartedAt = partyStartedAt,
                )
            },
        ) { session, _ ->
            session.snapshot(now).finishedReason != null
        }
}
