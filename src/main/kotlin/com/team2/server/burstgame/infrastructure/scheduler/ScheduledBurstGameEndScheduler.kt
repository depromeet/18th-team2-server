package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.service.BurstGameEndScheduler
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class ScheduledBurstGameEndScheduler : BurstGameEndScheduler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun schedule(
        partyId: Long,
        endsAt: LocalDateTime,
        onEnd: (Long) -> Unit,
    ) {
        val delayMillis = maxOf(0L, Duration.between(LocalDateTime.now(), endsAt).toMillis())
        executor.schedule(
            {
                runCatching {
                    onEnd(partyId)
                }.onFailure { ex ->
                    log.error("Failed to end scheduled burst game. partyId={}", partyId, ex)
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
