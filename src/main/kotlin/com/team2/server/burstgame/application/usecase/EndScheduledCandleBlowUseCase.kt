package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EndScheduledCandleBlowUseCase(
    private val sessionStore: CandleBlowSessionStore,
    private val eventBroadcaster: CandleBlowEventBroadcaster,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(
        partyId: Long,
        now: LocalDateTime,
    ): CandleBlowSnapshot? =
        sessionStore.withSessionLock(partyId) { session ->
            val endedNow = session.finishIfTimedOut(now)
            val snapshot = session.snapshot(now)
            if (endedNow && session.markAndCheckBroadcastNeeded(CandleBlowStatus.FINISHED)) {
                runCatching {
                    eventBroadcaster.broadcastEnded(snapshot)
                }.onFailure { ex ->
                    log.error("Failed to broadcast scheduled candle blow end. partyId={}", partyId, ex)
                }
            }
            snapshot
        }
}
