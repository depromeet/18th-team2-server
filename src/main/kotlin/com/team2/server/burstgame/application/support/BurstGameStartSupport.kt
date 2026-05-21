package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.Logger
import java.time.LocalDateTime

internal fun removeStartedSession(
    sessionService: BurstGameSessionService,
    log: Logger,
    partyId: Long,
    startedAt: LocalDateTime,
) {
    runCatching {
        sessionService.removeStarted(partyId, startedAt)
    }.onFailure { ex ->
        log.error("Failed to rollback started burst game session. partyId={} startedAt={}", partyId, startedAt, ex)
    }
}

internal fun endScheduledParty(
    sessionService: BurstGameSessionService,
    eventBroadcaster: BurstGameEventBroadcaster,
    partyId: Long,
) {
    val result = sessionService.end(partyId, LocalDateTime.now()) ?: return
    if (result.endedNow) {
        eventBroadcaster.broadcastEnded(result.snapshot)
    }
}

internal fun throwAlreadyEndedAfterBroadcast(
    eventBroadcaster: BurstGameEventBroadcaster,
    log: Logger,
    result: BurstGameSessionService.StartResult.AlreadyEnded,
): Nothing {
    if (result.endedNow) {
        runCatching {
            eventBroadcaster.broadcastEnded(result.snapshot)
        }.onFailure { ex ->
            log.error("Failed to broadcast burst game end after start retry. snapshot={}", result.snapshot, ex)
        }
    }
    throw BusinessException(ErrorCode.BURST_GAME_ALREADY_ENDED)
}
