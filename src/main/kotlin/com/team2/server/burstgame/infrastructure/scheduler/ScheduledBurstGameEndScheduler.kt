package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.service.BurstGameEndScheduler
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class ScheduledBurstGameEndScheduler : BurstGameEndScheduler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val scheduled = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val scheduledLock = Any()

    override fun schedule(
        partyId: Long,
        endsAt: LocalDateTime,
        onEnd: (Long) -> Unit,
    ) {
        val delayMillis = delayMillisUntil(endsAt)
        synchronized(scheduledLock) {
            scheduled[partyId]?.cancel(false)
            val future =
                executor.schedule(
                    {
                        synchronized(scheduledLock) {
                            scheduled.remove(partyId)
                        }
                        runCatching {
                            onEnd(partyId)
                        }.onFailure { ex ->
                            log.error("Failed to end scheduled burst game. partyId={}", partyId, ex)
                        }
                    },
                    delayMillis,
                    TimeUnit.MILLISECONDS,
                )
            scheduled[partyId] = future
        }
    }

    override fun cancel(partyId: Long): Boolean =
        synchronized(scheduledLock) {
            scheduled.remove(partyId)?.cancel(false) == true
        }

    private fun delayMillisUntil(endsAt: LocalDateTime): Long {
        val duration = Duration.between(LocalDateTime.now(), endsAt)
        if (!duration.isPositive) return 0
        return (duration.toNanos() + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
