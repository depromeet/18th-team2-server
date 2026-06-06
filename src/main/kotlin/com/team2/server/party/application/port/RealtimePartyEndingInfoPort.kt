package com.team2.server.party.application.port

import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.domain.entity.RealtimeParty
import java.time.LocalDateTime

interface RealtimePartyEndingInfoPort {
    fun get(party: RealtimeParty): RealtimePartyEndingInfo

    fun get(
        party: RealtimeParty,
        now: LocalDateTime,
    ): RealtimePartyEndingInfo
}
