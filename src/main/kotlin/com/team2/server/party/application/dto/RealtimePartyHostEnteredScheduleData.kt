package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.RealtimeParty
import java.time.LocalDateTime

data class RealtimePartyHostEnteredScheduleData(
    val partyId: Long,
    val hostEnteredAt: LocalDateTime,
) {
    companion object {
        fun from(party: RealtimeParty): RealtimePartyHostEnteredScheduleData =
            RealtimePartyHostEnteredScheduleData(
                partyId = party.id,
                hostEnteredAt = requireNotNull(party.hostEnteredAt),
            )
    }
}
