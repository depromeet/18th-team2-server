package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class InMemoryCandleBlowStatusReader(
    private val sessionStore: CandleBlowSessionStore,
    private val candleBlowProperties: CandleBlowProperties,
) : CandleBlowStatusReader {
    override fun isCandleBlowFinished(
        partyId: Long,
        hostEnteredAt: LocalDateTime?,
        now: LocalDateTime,
    ): Boolean =
        sessionStore.withSessionLock(partyId) { session ->
            session.snapshot(now).finishedReason != null
        } ?: hostEnteredAt?.let { enteredAt ->
            sessionStore.getOrCreateWithLock(
                partyId = partyId,
                sessionFactory = {
                    CandleBlowSession.fromHostEnteredAt(
                        partyId = partyId,
                        hostEnteredAt = enteredAt,
                        durationSeconds = candleBlowProperties.durationSeconds,
                    )
                },
            ) { session, _ ->
                session.snapshot(now).finishedReason != null
            }
        } ?: false
}
