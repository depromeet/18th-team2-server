package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.service.BurstGameEndScheduler
import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameParticipantReader
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.domain.BurstGameSnapshot
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
    fun start(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): BurstGameSnapshot {
        val participant = participantReader.resolve(partyId, userId, participantToken)
        val result = sessionService.start(partyId, participant, LocalDateTime.now())
        if (result.created) {
            runCatching {
                endScheduler.schedule(result.snapshot.roundId, result.snapshot.endsAt)
                eventBroadcaster.broadcastStarted(result.snapshot)
            }.onFailure { ex ->
                sessionService.remove(result.snapshot.roundId)
                log.error("Failed to complete burst game start. snapshot={}", result.snapshot, ex)
                throw ex
            }
        }
        return result.snapshot
    }
}
