package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.port.CandleBlowScheduler
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class ScheduledCandleBlowScheduler(
    private val clock: Clock,
) : CandleBlowScheduler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "candle-blow-scheduler")
        }
    private val scheduled = ConcurrentHashMap<Long, CandleBlowScheduledTasks>()
    private val scheduledLock = Any()

    override fun scheduleEnd(
        partyId: Long,
        endsAt: LocalDateTime,
        onEnd: (Long) -> Unit,
    ) {
        val delayMillis = delayMillisUntil(endsAt)
        synchronized(scheduledLock) {
            val tasks = scheduled.computeIfAbsent(partyId) { CandleBlowScheduledTasks() }
            tasks.end?.cancel(false)
            tasks.end =
                executor.schedule(
                    {
                        synchronized(scheduledLock) {
                            scheduled[partyId]?.end = null
                            removeIfEmpty(partyId)
                        }
                        runCatching {
                            onEnd(partyId)
                        }.onFailure { ex ->
                            log.error("Failed to end scheduled candle blow. partyId={}", partyId, ex)
                        }
                    },
                    delayMillis,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    override fun cancel(partyId: Long): Boolean =
        synchronized(scheduledLock) {
            val tasks = scheduled.remove(partyId) ?: return@synchronized false
            val endCancelled = tasks.end?.cancel(false) == true
            endCancelled
        }

    private fun removeIfEmpty(partyId: Long) {
        val tasks = scheduled[partyId] ?: return
        if (tasks.end == null) {
            scheduled.remove(partyId, tasks)
        }
    }

    private fun delayMillisUntil(targetAt: LocalDateTime): Long {
        val duration = Duration.between(LocalDateTime.now(clock), targetAt)
        if (!duration.isPositive) return 0
        return (duration.toNanos() + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private data class CandleBlowScheduledTasks(
        var end: ScheduledFuture<*>? = null,
    )

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
