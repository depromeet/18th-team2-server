package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.port.BurstGameEndScheduler
import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.service.BurstGameSessionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.LocalDateTime

@Component
class BurstGameStartSideEffectHandler(
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
    private val endScheduler: BurstGameEndScheduler,
    private val candleBlowSessionStore: CandleBlowSessionStore,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(startResult: BurstGameSessionService.StartResult): BurstGameSessionService.StartResult.Started =
        when (startResult) {
            is BurstGameSessionService.StartResult.Started -> startResult
            is BurstGameSessionService.StartResult.AlreadyEnded ->
                throwAlreadyEndedAfterBroadcast(eventBroadcaster, log, startResult)
        }

    fun completeStartedAfterCommit(
        partyId: Long,
        result: BurstGameSessionService.StartResult.Started,
        now: LocalDateTime,
    ) {
        check(TransactionSynchronizationManager.isSynchronizationActive()) {
            "completeStartedAfterCommit must be called within a transactional context."
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    completeStarted(partyId, result, now)
                }
            },
        )
    }

    private fun completeStarted(
        partyId: Long,
        result: BurstGameSessionService.StartResult.Started,
        now: LocalDateTime,
    ) {
        if (!result.created) return
        scheduleEnd(partyId, result, now)
        broadcastStarted(partyId, result, now)
        removeCandleBlowSession(partyId)
    }

    private fun scheduleEnd(
        partyId: Long,
        result: BurstGameSessionService.StartResult.Started,
        now: LocalDateTime,
    ) {
        runCatching {
            endScheduler.schedule(partyId, result.snapshot.endsAt) {
                endScheduledParty(sessionService, eventBroadcaster, it, LocalDateTime.now(clock))
            }
        }.onFailure { ex ->
            rollbackStartedSession(partyId, result, now)
            logStartFailure(result, ex)
            throw ex
        }
    }

    private fun broadcastStarted(
        partyId: Long,
        result: BurstGameSessionService.StartResult.Started,
        now: LocalDateTime,
    ) {
        runCatching {
            eventBroadcaster.broadcastStarted(result.snapshot)
        }.onFailure { ex ->
            cancelEndSchedule(partyId)
            rollbackStartedSession(partyId, result, now)
            logStartFailure(result, ex)
            throw ex
        }
    }

    private fun cancelEndSchedule(partyId: Long) {
        runCatching {
            endScheduler.cancel(partyId)
        }.onFailure { ex ->
            log.error("Failed to cancel burst game end schedule during start rollback. partyId={}", partyId, ex)
        }
    }

    private fun rollbackStartedSession(
        partyId: Long,
        result: BurstGameSessionService.StartResult.Started,
        now: LocalDateTime,
    ) {
        runCatching {
            removeStartedSession(sessionService, log, partyId, result.snapshot.startedAt, now)
        }.onFailure { ex ->
            log.error("Failed to remove burst game session during start rollback. partyId={}", partyId, ex)
        }
    }

    private fun removeCandleBlowSession(partyId: Long) {
        runCatching {
            candleBlowSessionStore.removeByPartyId(partyId)
        }.onFailure { ex ->
            log.warn("Failed to remove candle blow session after burst game start. partyId={}", partyId, ex)
        }
    }

    private fun logStartFailure(
        result: BurstGameSessionService.StartResult.Started,
        ex: Throwable,
    ) {
        log.error(
            "Failed to complete burst game start. partyId={} startedAt={} stateVersion={}",
            result.snapshot.partyId,
            result.snapshot.startedAt,
            result.snapshot.stateVersion,
            ex,
        )
    }
}
