package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class InMemoryCandleBlowStatusReader(
    private val sessionStore: CandleBlowSessionStore,
) : CandleBlowStatusReader {
    override fun isCandleBlowFinished(
        partyId: Long,
        now: LocalDateTime,
    ): Boolean =
        sessionStore.withSessionLock(partyId) { session ->
            session.snapshot(now).finishedReason != null
        } == true
}
