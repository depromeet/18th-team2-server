package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowScheduleResult
import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowScheduler
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
class StartCandleBlowUseCase(
    private val sessionStore: CandleBlowSessionStore,
    private val eventBroadcaster: CandleBlowEventBroadcaster,
    private val scheduler: CandleBlowScheduler,
    private val endScheduledCandleBlowUseCase: EndScheduledCandleBlowUseCase,
    private val candleBlowProperties: CandleBlowProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(
        partyId: Long,
        startedAt: LocalDateTime,
    ): CandleBlowScheduleResult =
        sessionStore.getOrCreateWithLock(
            partyId = partyId,
            sessionFactory = {
                CandleBlowSession.fromStartedAt(
                    partyId = partyId,
                    startedAt = startedAt,
                    durationSeconds = candleBlowProperties.durationSeconds,
                )
            },
        ) { session, created ->
            val snapshot = session.snapshot(startedAt)
            if (created) {
                scheduler.scheduleEnd(partyId, session.endsAt) { scheduledPartyId ->
                    endScheduledCandleBlowUseCase(scheduledPartyId, session.endsAt)
                }
            }
            if (snapshot.status == CandleBlowStatus.ACTIVE && session.markAndCheckBroadcastNeeded(snapshot.status)) {
                broadcastStarted(snapshot)
            }
            CandleBlowScheduleResult.from(snapshot)
        }

    private fun broadcastStarted(snapshot: CandleBlowSnapshot) {
        runCatching {
            eventBroadcaster.broadcastStarted(snapshot)
        }.onFailure { ex ->
            log.error("Failed to broadcast candle blow start. partyId={}", snapshot.partyId, ex)
        }
    }
}
