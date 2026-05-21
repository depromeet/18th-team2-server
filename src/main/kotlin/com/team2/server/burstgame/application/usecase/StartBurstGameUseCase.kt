package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.StartBurstGameResponse
import com.team2.server.burstgame.application.port.BurstGameEndScheduler
import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.endScheduledParty
import com.team2.server.burstgame.application.support.removeStartedSession
import com.team2.server.burstgame.application.support.throwAlreadyEndedAfterBroadcast
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StartBurstGameUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
    private val endScheduler: BurstGameEndScheduler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): StartBurstGameResponse {
        val participant = participantResolver.resolve(partyId, userId, participantToken)
        val now = LocalDateTime.now()
        val result =
            when (val startResult = sessionService.start(partyId, participant, now)) {
                is BurstGameSessionService.StartResult.Started -> startResult
                is BurstGameSessionService.StartResult.AlreadyEnded ->
                    throwAlreadyEndedAfterBroadcast(eventBroadcaster, log, startResult)
            }
        if (result.created) {
            runCatching {
                endScheduler.schedule(partyId, result.snapshot.endsAt) {
                    endScheduledParty(sessionService, eventBroadcaster, it)
                }
            }.onFailure { ex ->
                removeStartedSession(sessionService, log, partyId, result.snapshot.startedAt, now)
                logStartFailure(result, ex)
                throw ex
            }
            runCatching {
                eventBroadcaster.broadcastStarted(result.snapshot)
            }.onFailure { ex ->
                endScheduler.cancel(partyId)
                removeStartedSession(sessionService, log, partyId, result.snapshot.startedAt, now)
                logStartFailure(result, ex)
                throw ex
            }
        }
        return StartBurstGameResponse.from(result.snapshot)
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
