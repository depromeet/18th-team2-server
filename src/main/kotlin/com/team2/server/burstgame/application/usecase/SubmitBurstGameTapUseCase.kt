package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameParticipantReader
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.domain.BurstGameTapResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SubmitBurstGameTapUseCase(
    private val participantReader: BurstGameParticipantReader,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
) {
    @Transactional
    fun submit(
        roundId: String,
        userId: Long?,
        participantToken: String?,
        tapCount: Int,
        clientSequence: Long,
    ): BurstGameTapResult {
        val now = LocalDateTime.now()
        val partyId = sessionService.findPartyIdByRoundId(roundId, now)
        val participant = participantReader.resolve(partyId, userId, participantToken)
        val result = sessionService.submit(roundId, participant, tapCount, clientSequence, now)
        if (result.accepted) {
            eventBroadcaster.broadcastProgress(result.snapshot)
        }
        if (result.endedNow) {
            eventBroadcaster.broadcastEnded(result.snapshot)
        }
        return result
    }
}
