package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.RealtimeParty
import java.time.LocalDateTime

data class RealtimePartyScheduleData(
    val partyId: Long,
    val startedAt: LocalDateTime,
) {
    companion object {
        fun from(party: RealtimeParty): RealtimePartyScheduleData =
            RealtimePartyScheduleData(
                partyId = party.id,
                startedAt = party.startedAt,
            )
    }
}
