package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.application.service.BurstGameParticipantReader
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.domain.BurstGameSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetBurstGameSnapshotUseCase(
    private val participantReader: BurstGameParticipantReader,
    private val sessionService: BurstGameSessionService,
    private val eventBroadcaster: BurstGameEventBroadcaster,
) {
    @Transactional(readOnly = true)
    fun get(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): BurstGameSnapshot {
        val participant = participantReader.resolve(partyId, userId, participantToken)
        val result = sessionService.snapshot(partyId, participant, LocalDateTime.now())
        if (result.endedNow) {
            eventBroadcaster.broadcastEnded(result.snapshot)
        }
        return result.snapshot
    }
}
