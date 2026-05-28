package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.port.CandleBlowScheduler
import com.team2.server.burstgame.application.usecase.EndScheduledCandleBlowUseCase
import com.team2.server.burstgame.application.usecase.RecoverCandleBlowScheduleUseCase
import com.team2.server.burstgame.application.usecase.StartScheduledCandleBlowUseCase
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
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
    private val recoverCandleBlowScheduleUseCase: RecoverCandleBlowScheduleUseCase,
    private val startScheduledCandleBlowUseCase: StartScheduledCandleBlowUseCase,
    private val endScheduledCandleBlowUseCase: EndScheduledCandleBlowUseCase,
) : CandleBlowScheduler {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "candle-blow-scheduler")
        }
    private val scheduled = ConcurrentHashMap<Long, CandleBlowScheduledTasks>()
    private val scheduledLock = Any()

    @PostConstruct
    fun recoverSchedules() {
        var lastFailure: DataAccessException? = null
        repeat(RECOVERY_MAX_ATTEMPTS) { attemptIndex ->
            try {
                recoverSchedulesOnce()
                return
            } catch (ex: DataAccessException) {
                lastFailure = ex
                val attempt = attemptIndex + 1
                if (attempt == RECOVERY_MAX_ATTEMPTS) {
                    log.error("Failed to recover candle blow schedules. attempt={}", attempt, ex)
                } else {
                    log.warn("Failed to recover candle blow schedules. attempt={}", attempt, ex)
                    sleepBeforeRecoveryRetry()
                }
            }
        }
        throw IllegalStateException("Failed to recover candle blow schedules.", lastFailure)
    }

    private fun recoverSchedulesOnce() {
        recoverCandleBlowScheduleUseCase().forEach { target ->
            scheduleParty(target.partyId, target.partyStartedAt)
        }
    }

    private fun sleepBeforeRecoveryRetry() {
        try {
            Thread.sleep(RECOVERY_RETRY_DELAY_MILLIS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while retrying candle blow schedule recovery.", ex)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRealtimePartyCreated(event: RealtimePartyCreatedEvent) {
        scheduleParty(event.partyId, event.startedAt)
    }

    fun scheduleParty(
        partyId: Long,
        partyStartedAt: LocalDateTime,
    ) {
        val startsAt = partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)
        val endsAt = startsAt.plusSeconds(CandleBlowPolicy.DURATION_SECONDS)
        scheduleStart(partyId, startsAt) { scheduledPartyId ->
            val snapshot =
                startScheduledCandleBlowUseCase(
                    partyId = scheduledPartyId,
                    partyStartedAt = partyStartedAt,
                    now = LocalDateTime.now(clock),
                )
            if (snapshot.status != CandleBlowStatus.FINISHED) {
                scheduleEnd(scheduledPartyId, endsAt) { endPartyId ->
                    endScheduledCandleBlowUseCase(endPartyId, LocalDateTime.now(clock))
                }
            }
        }
    }

    override fun scheduleStart(
        partyId: Long,
        startsAt: LocalDateTime,
        onStart: (Long) -> Unit,
    ) {
        val delayMillis = delayMillisUntil(startsAt)
        synchronized(scheduledLock) {
            val tasks = scheduled.computeIfAbsent(partyId) { CandleBlowScheduledTasks() }
            tasks.start?.cancel(false)
            tasks.start =
                executor.schedule(
                    {
                        synchronized(scheduledLock) {
                            scheduled[partyId]?.start = null
                            removeIfEmpty(partyId)
                        }
                        runCatching {
                            onStart(partyId)
                        }.onFailure { ex ->
                            log.error("Failed to start scheduled candle blow. partyId={}", partyId, ex)
                        }
                    },
                    delayMillis,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

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
            val startCancelled = tasks.start?.cancel(false) == true
            val endCancelled = tasks.end?.cancel(false) == true
            startCancelled || endCancelled
        }

    private fun removeIfEmpty(partyId: Long) {
        val tasks = scheduled[partyId] ?: return
        if (tasks.start == null && tasks.end == null) {
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
        var start: ScheduledFuture<*>? = null,
        var end: ScheduledFuture<*>? = null,
    )

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val RECOVERY_MAX_ATTEMPTS = 3
        const val RECOVERY_RETRY_DELAY_MILLIS = 1_000L
    }
}
