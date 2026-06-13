package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowScheduleResult
import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StartScheduledCandleBlowUseCase(
    private val sessionStore: CandleBlowSessionStore,
    private val eventBroadcaster: CandleBlowEventBroadcaster,
    private val candleBlowProperties: CandleBlowProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(
        partyId: Long,
        hostEnteredAt: LocalDateTime,
        now: LocalDateTime,
    ): CandleBlowScheduleResult =
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
            val snapshot = session.snapshot(now)
            if (session.markAndCheckBroadcastNeeded(snapshot.status)) {
                broadcastStartOrEnd(snapshot)
            }
            CandleBlowScheduleResult.from(snapshot)
        }

    private fun broadcastStartOrEnd(snapshot: CandleBlowSnapshot) {
        runCatching {
            when (snapshot.status) {
                CandleBlowStatus.ACTIVE -> eventBroadcaster.broadcastStarted(snapshot)
                CandleBlowStatus.FINISHED -> eventBroadcaster.broadcastEnded(snapshot)
                CandleBlowStatus.WAITING -> Unit
            }
        }.onFailure { ex ->
            log.error("Failed to broadcast scheduled candle blow state. partyId={}", snapshot.partyId, ex)
        }
    }
}
