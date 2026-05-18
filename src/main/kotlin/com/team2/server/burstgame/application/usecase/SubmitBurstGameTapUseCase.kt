package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.api.dto.SubmitBurstGameTapResponse
import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameParticipantReader
import com.team2.server.burstgame.application.service.BurstGameRoundQueryService
import com.team2.server.burstgame.application.service.BurstGameSessionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SubmitBurstGameTapUseCase(
    private val participantReader: BurstGameParticipantReader,
    private val roundQueryService: BurstGameRoundQueryService,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    operator fun invoke(
        roundId: String,
        userId: Long?,
        participantToken: String?,
        tapCount: Int,
        clientSequence: Long,
    ): SubmitBurstGameTapResponse {
        val now = LocalDateTime.now()
        val partyId = roundQueryService.findPartyIdByRoundId(roundId, now)
        val participant = participantReader.resolve(partyId, userId, participantToken)
        val result = sessionService.submit(roundId, participant, tapCount, clientSequence, now)
        if (result.accepted) {
            runCatching {
                eventBroadcaster.broadcastProgress(result.snapshot)
            }.onFailure { ex ->
                log.error("Failed to broadcast burst game progress. result={}", result, ex)
            }
        }
        if (result.endedNow) {
            runCatching {
                eventBroadcaster.broadcastEnded(result.snapshot)
            }.onFailure { ex ->
                log.error("Failed to broadcast burst game end after submit. result={}", result, ex)
            }
        }
        return SubmitBurstGameTapResponse.from(result)
    }
}
