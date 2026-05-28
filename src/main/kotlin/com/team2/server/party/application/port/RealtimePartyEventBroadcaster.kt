package com.team2.server.party.application.port

import java.time.LocalDateTime

interface RealtimePartyEventBroadcaster {
    fun broadcastPartyEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
    )

    fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
    )

    fun completeParty(partyId: Long)
}
