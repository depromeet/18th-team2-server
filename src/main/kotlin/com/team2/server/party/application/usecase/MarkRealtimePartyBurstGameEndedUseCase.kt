package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.RealtimePartyBurstGameEndRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkRealtimePartyBurstGameEndedUseCase(
    private val recorder: RealtimePartyBurstGameEndRecorder,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        endedAt: LocalDateTime,
    ) {
        recorder.recordFirst(partyId, endedAt)
    }
}
