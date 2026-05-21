package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.SubmitBurstGameTapResponse
import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SubmitBurstGameTapUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        tapCount: Int,
        clientSequence: Long,
    ): SubmitBurstGameTapResponse {
        val now = LocalDateTime.now()
        val participant = participantResolver.resolve(partyId, userId, participantToken)
        val result = sessionService.submit(partyId, participant, tapCount, clientSequence, now)
        if (result.accepted) {
            runCatching {
                eventBroadcaster.broadcastProgress(result.snapshot)
            }.onFailure { ex ->
                log.error(
                    "Failed to broadcast burst game progress. partyId={} participantId={} stateVersion={}",
                    result.snapshot.partyId,
                    result.snapshot.myParticipantId,
                    result.snapshot.stateVersion,
                    ex,
                )
            }
        }
        if (result.endedNow) {
            runCatching {
                eventBroadcaster.broadcastEnded(result.snapshot)
            }.onFailure { ex ->
                log.error(
                    "Failed to broadcast burst game end after submit. partyId={} participantId={} stateVersion={}",
                    result.snapshot.partyId,
                    result.snapshot.myParticipantId,
                    result.snapshot.stateVersion,
                    ex,
                )
            }
        }
        return SubmitBurstGameTapResponse.from(result)
    }
}
