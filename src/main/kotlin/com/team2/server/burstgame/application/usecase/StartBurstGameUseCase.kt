package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.api.dto.StartBurstGameResponse
import com.team2.server.burstgame.application.service.BurstGameEndScheduler
import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameParticipantReader
import com.team2.server.burstgame.application.service.BurstGameSessionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StartBurstGameUseCase(
    private val participantReader: BurstGameParticipantReader,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
    private val endScheduler: BurstGameEndScheduler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): StartBurstGameResponse {
        val participant = participantReader.resolve(partyId, userId, participantToken)
        val result = sessionService.start(partyId, participant, LocalDateTime.now())
        if (result.created) {
            runCatching {
                endScheduler.schedule(partyId, result.snapshot.endsAt, ::endScheduledParty)
                eventBroadcaster.broadcastStarted(result.snapshot)
            }.onFailure { ex ->
                sessionService.remove(partyId)
                log.error("Failed to complete burst game start. snapshot={}", result.snapshot, ex)
                throw ex
            }
        }
        return StartBurstGameResponse.from(result.snapshot)
    }

    private fun endScheduledParty(partyId: Long) {
        val result = sessionService.end(partyId, LocalDateTime.now()) ?: return
        if (result.endedNow) {
            eventBroadcaster.broadcastEnded(result.snapshot)
        }
    }
}
