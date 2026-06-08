package com.team2.server.party.infrastructure.sse

import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.event.RealtimePartyBurstGameEndedEvent
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.event.RealtimePartyEndingStartedEvent
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.usecase.RecoverRealtimePartyEndScheduleUseCase
import com.team2.server.party.application.usecase.StartAutomaticRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
@Suppress("TooManyFunctions")
class PartyEndScheduler(
    private val taskScheduler: TaskScheduler,
    private val realtimePartyEventBroadcaster: RealtimePartyEventBroadcaster,
    private val recoverRealtimePartyEndScheduleUseCase: RecoverRealtimePartyEndScheduleUseCase,
    private val startAutomaticRealtimePartyEndUseCase: StartAutomaticRealtimePartyEndUseCase,
    private val clock: Clock,
    private val phaseStore: PartyPhaseStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val partyStates = ConcurrentHashMap<Long, PartyEndScheduleState>()

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
                    log.error("Failed to recover realtime party end schedules. attempt={}", attempt, ex)
                } else {
                    log.warn("Failed to recover realtime party end schedules. attempt={}", attempt, ex)
                    sleepBeforeRecoveryRetry()
                }
            }
        }
        throw IllegalStateException("Failed to recover realtime party end schedules.", lastFailure)
    }

    private fun recoverSchedulesOnce() {
        val result = recoverRealtimePartyEndScheduleUseCase()
        result.automaticEndSchedules.forEach { scheduleAutomaticEnd(it.partyId, it.endingStartedAt) }
        result.endingTargets.forEach { scheduleEndingEvents(it, emitEnding = false) }
    }

    private fun sleepBeforeRecoveryRetry() {
        try {
            Thread.sleep(RECOVERY_RETRY_DELAY_MILLIS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while retrying realtime party end schedule recovery.", ex)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRealtimePartyCreated(event: RealtimePartyCreatedEvent) {
        scheduleAutomaticEnd(event.partyId, event.startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRealtimePartyEndingStarted(event: RealtimePartyEndingStartedEvent) {
        scheduleEndingEvents(
            RealtimeEndingScheduleTarget(
                partyId = event.partyId,
                endingStartedAt = event.endingStartedAt,
                endedAt = event.endedAt,
                endingReason = event.endingReason,
                hostNickname = event.hostNickname,
            ),
            emitEnding = true,
        )
        phaseStore.forceSet(event.partyId, PartyPhase.END, event.endingStartedAt)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameEnded(event: RealtimePartyBurstGameEndedEvent) {
        phaseStore.advance(event.partyId, PartyPhase.BURST, PartyPhase.CLOSEABLE, event.endedAt)
    }

    private fun scheduleAutomaticEnd(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ) {
        val state = stateFor(partyId)
        if (synchronized(state) { state.isPartyEndingHandled() }) return
        val task =
            taskScheduler.schedule(
                { startAutomaticEnding(partyId, endingStartedAt) },
                endingStartedAt.toInstant(),
            )
        synchronized(state) {
            if (state.isPartyEndingHandled()) {
                task.cancel(false)
                return
            }
            state.automaticEndTask?.cancel(false)
            state.automaticEndTask = task
        }
    }

    private fun startAutomaticEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ) {
        val state = stateFor(partyId)
        synchronized(state) {
            state.automaticEndTask = null
        }
        runCatching {
            val target = startAutomaticRealtimePartyEndUseCase(partyId, endingStartedAt)
            if (target != null) scheduleEndingEvents(target, emitEnding = true)
        }.onFailure { ex ->
            log.error("Failed to start automatic realtime party end. partyId={}", partyId, ex)
        }
    }

    private fun scheduleEndingEvents(
        target: RealtimeEndingScheduleTarget,
        emitEnding: Boolean,
    ) {
        val state = stateFor(target.partyId)
        synchronized(state) {
            state.automaticEndTask?.cancel(false)
            state.automaticEndTask = null
            if (state.endedTask == null) {
                state.endedTask =
                    taskScheduler.schedule(
                        { sendPartyEnded(target) },
                        target.endedAt.toInstant(),
                    )
            }
        }
        if (emitEnding) sendPartyEnding(target)
    }

    private fun sendPartyEnding(target: RealtimeEndingScheduleTarget) {
        val state = stateFor(target.partyId)
        val shouldSend =
            synchronized(state) {
                if (state.endingNotified || state.endedNotified) {
                    false
                } else {
                    state.endingNotified = true
                    true
                }
            }
        if (!shouldSend) return
        realtimePartyEventBroadcaster.broadcastPartyEnding(
            partyId = target.partyId,
            endingStartedAt = target.endingStartedAt,
            endedAt = target.endedAt,
            endingReason = target.endingReason,
            hostNickname = target.hostNickname,
        )
    }

    private fun sendPartyEnded(target: RealtimeEndingScheduleTarget) {
        val state = stateFor(target.partyId)
        val shouldSend =
            synchronized(state) {
                if (state.endedNotified) {
                    false
                } else {
                    state.endedNotified = true
                    state.endedTask = null
                    state.automaticEndTask?.cancel(false)
                    state.automaticEndTask = null
                    true
                }
            }
        if (!shouldSend) return
        realtimePartyEventBroadcaster.broadcastPartyEnded(target.partyId, target.endedAt, target.hostNickname)
        taskScheduler.schedule(
            {
                realtimePartyEventBroadcaster.completeParty(target.partyId)
                phaseStore.removeByPartyId(target.partyId)
                partyStates.remove(target.partyId, state)
            },
            Instant
                .now(clock)
                .plusSeconds(GRACE_CLEANUP_SECONDS),
        )
    }

    private fun stateFor(partyId: Long): PartyEndScheduleState =
        partyStates.computeIfAbsent(partyId) { PartyEndScheduleState() }

    private fun PartyEndScheduleState.isPartyEndingHandled(): Boolean = endingNotified || endedNotified

    private fun LocalDateTime.toInstant(): Instant = atZone(clock.zone).toInstant()

    @PreDestroy
    fun cancelSchedules() {
        partyStates.values.forEach { state ->
            synchronized(state) {
                state.automaticEndTask?.cancel(false)
                state.endedTask?.cancel(false)
            }
        }
    }

    companion object {
        private const val GRACE_CLEANUP_SECONDS = 5L
        private const val RECOVERY_MAX_ATTEMPTS = 3
        private const val RECOVERY_RETRY_DELAY_MILLIS = 1_000L
    }

    private class PartyEndScheduleState {
        var automaticEndTask: ScheduledFuture<*>? = null
        var endedTask: ScheduledFuture<*>? = null
        var endingNotified: Boolean = false
        var endedNotified: Boolean = false
    }
}
