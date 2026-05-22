package com.team2.server.chat.infrastructure.sse

import com.team2.server.burstgame.application.event.BurstGameEndedEvent
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.event.RealtimePartyEndingStartedEvent
import com.team2.server.party.application.usecase.HandleBurstGameEndedUseCase
import com.team2.server.party.application.usecase.RecoverRealtimePartyEndScheduleUseCase
import com.team2.server.party.application.usecase.StartAutomaticRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.RealtimeParty
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
@Suppress("TooManyFunctions")
class PartyEndScheduler(
    private val taskScheduler: TaskScheduler,
    private val sseEmitterRegistry: SseEmitterRegistry,
    private val recoverRealtimePartyEndScheduleUseCase: RecoverRealtimePartyEndScheduleUseCase,
    private val startAutomaticRealtimePartyEndUseCase: StartAutomaticRealtimePartyEndUseCase,
    private val handleBurstGameEndedUseCase: HandleBurstGameEndedUseCase,
    private val clock: Clock,
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

    fun scheduleIfNeeded(
        partyId: Long,
        startedAt: LocalDateTime,
    ) {
        scheduleHostEndAvailable(partyId, startedAt)
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
            ),
            emitEnding = true,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameEnded(event: BurstGameEndedEvent) {
        if (handleBurstGameEndedUseCase(event.partyId)) {
            sendHostEndAvailableIfNeeded(event.partyId, event.endedAt)
        }
    }

    fun sendHostEndAvailable(
        partyId: Long,
        availableAt: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcastHost(
            partyId,
            SseEmitter
                .event()
                .name("host-end-available")
                .data(HostEndAvailablePayload(partyId = partyId, availableAt = availableAt))
                .build(),
        )
    }

    private fun scheduleHostEndAvailable(
        partyId: Long,
        startedAt: LocalDateTime,
    ) {
        val state = stateFor(partyId)
        synchronized(state) {
            if (state.hostAvailableScheduled || state.hostAvailableNotified) return
            state.hostAvailableScheduled = true
        }

        val availableAt = startedAt.plusMinutes(RealtimeParty.HOST_END_AVAILABLE_AFTER_MINUTES)
        val task = scheduleHostEndAvailableTask(state, partyId, availableAt)
        storeHostEndAvailableTask(state, task)
    }

    private fun sendHostEndAvailableIfNeeded(
        partyId: Long,
        availableAt: LocalDateTime,
    ) {
        val state = stateFor(partyId)
        val shouldSend =
            synchronized(state) {
                if (state.hostAvailableNotified || state.endingNotified || state.endedNotified) {
                    false
                } else {
                    cancelHostEndAvailable(state)
                    state.hostAvailableNotified = true
                    true
                }
            }
        if (shouldSend) sendHostEndAvailable(partyId, availableAt)
    }

    private fun scheduleHostEndAvailableTask(
        state: PartyEndScheduleState,
        partyId: Long,
        availableAt: LocalDateTime,
    ): ScheduledFuture<*> =
        taskScheduler.schedule(
            {
                val shouldSend =
                    synchronized(state) {
                        if (!state.hostAvailableScheduled) {
                            false
                        } else {
                            state.hostAvailableTask = null
                            state.hostAvailableScheduled = false
                            state.hostAvailableNotified = true
                            true
                        }
                    }
                if (shouldSend) sendHostEndAvailable(partyId, availableAt)
            },
            availableAt.toInstant(),
        )

    private fun storeHostEndAvailableTask(
        state: PartyEndScheduleState,
        task: ScheduledFuture<*>,
    ) {
        synchronized(state) {
            if (state.hostAvailableScheduled) {
                state.hostAvailableTask = task
            } else {
                task.cancel(false)
            }
        }
    }

    private fun scheduleAutomaticEnd(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ) {
        val state = stateFor(partyId)
        val task =
            taskScheduler.schedule(
                { startAutomaticEnding(partyId, endingStartedAt) },
                endingStartedAt.toInstant(),
            )
        synchronized(state) {
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
            cancelHostEndAvailable(state)
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
        sseEmitterRegistry.broadcast(
            target.partyId,
            SseEmitter
                .event()
                .name("party-ending")
                .data(
                    PartyEndingPayload(
                        partyId = target.partyId,
                        endingStartedAt = target.endingStartedAt,
                        endedAt = target.endedAt,
                    ),
                ).build(),
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
                    cancelHostEndAvailable(state)
                    state.automaticEndTask?.cancel(false)
                    state.automaticEndTask = null
                    true
                }
            }
        if (!shouldSend) return
        sseEmitterRegistry.broadcast(
            target.partyId,
            SseEmitter
                .event()
                .name("party-ended")
                .data(PartyEndedPayload(partyId = target.partyId, endedAt = target.endedAt))
                .build(),
        )
        taskScheduler.schedule(
            {
                sseEmitterRegistry.completeAll(target.partyId)
                partyStates.remove(target.partyId, state)
            },
            Instant
                .now(clock)
                .plusSeconds(GRACE_CLEANUP_SECONDS),
        )
    }

    private fun cancelHostEndAvailable(state: PartyEndScheduleState) {
        state.hostAvailableTask?.cancel(false)
        state.hostAvailableTask = null
        state.hostAvailableScheduled = false
    }

    private fun stateFor(partyId: Long): PartyEndScheduleState =
        partyStates.computeIfAbsent(partyId) { PartyEndScheduleState() }

    private fun LocalDateTime.toInstant(): Instant = atZone(clock.zone).toInstant()

    @PreDestroy
    fun cancelSchedules() {
        partyStates.values.forEach { state ->
            synchronized(state) {
                cancelHostEndAvailable(state)
                state.automaticEndTask?.cancel(false)
                state.endedTask?.cancel(false)
            }
        }
    }

    data class HostEndAvailablePayload(
        val partyId: Long,
        val availableAt: LocalDateTime,
    )

    data class PartyEndingPayload(
        val partyId: Long,
        val endingStartedAt: LocalDateTime,
        val endedAt: LocalDateTime,
    )

    data class PartyEndedPayload(
        val partyId: Long,
        val endedAt: LocalDateTime,
    )

    companion object {
        private const val GRACE_CLEANUP_SECONDS = 2L
        private const val RECOVERY_MAX_ATTEMPTS = 3
        private const val RECOVERY_RETRY_DELAY_MILLIS = 1_000L
    }

    private class PartyEndScheduleState {
        var hostAvailableScheduled: Boolean = false
        var hostAvailableNotified: Boolean = false
        var hostAvailableTask: ScheduledFuture<*>? = null
        var automaticEndTask: ScheduledFuture<*>? = null
        var endedTask: ScheduledFuture<*>? = null
        var endingNotified: Boolean = false
        var endedNotified: Boolean = false
    }
}
