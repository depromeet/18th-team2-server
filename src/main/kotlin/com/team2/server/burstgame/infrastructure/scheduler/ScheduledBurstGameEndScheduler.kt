package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.service.BurstGameEndScheduler
import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameSessionService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class ScheduledBurstGameEndScheduler(
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
) : BurstGameEndScheduler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun schedule(
        roundId: String,
        endsAt: LocalDateTime,
    ) {
        val delayMillis = maxOf(0L, Duration.between(LocalDateTime.now(), endsAt).toMillis())
        executor.schedule(
            {
                runCatching {
                    val result = sessionService.end(roundId, LocalDateTime.now())
                    if (result?.endedNow == true) {
                        eventBroadcaster.broadcastEnded(result.snapshot)
                    }
                }.onFailure { ex ->
                    log.error("Failed to end scheduled burst game round. roundId={}", roundId, ex)
                }
            },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}
