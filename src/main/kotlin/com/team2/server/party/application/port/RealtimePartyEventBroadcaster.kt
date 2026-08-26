package com.team2.server.party.application.port

import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

interface RealtimePartyEventBroadcaster {
    fun broadcastPartyEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
        hostNickname: String,
        serverNow: LocalDateTime,
    )

    fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
        hostNickname: String,
        serverNow: LocalDateTime,
    )

    fun completeParty(partyId: Long)

    fun broadcastPhaseChanged(
        partyId: Long,
        phase: PartyPhase,
        phaseStartedAt: LocalDateTime,
        serverNow: LocalDateTime,
    )
}
