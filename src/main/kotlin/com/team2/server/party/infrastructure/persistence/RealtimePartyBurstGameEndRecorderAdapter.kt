package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.application.port.RealtimePartyBurstGameEndRecorder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyBurstGameEndRecorderAdapter(
    private val partyRepository: PartyRepository,
) : RealtimePartyBurstGameEndRecorder {
    override fun recordFirst(
        partyId: Long,
        endedAt: LocalDateTime,
    ): Boolean = partyRepository.markBurstGameEndedIfAbsent(partyId, endedAt) == 1
}
