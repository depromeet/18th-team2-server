package com.team2.server.party.application.port

import java.time.LocalDateTime

interface RealtimePartyBurstGameEndRecorder {
    fun recordFirst(
        partyId: Long,
        endedAt: LocalDateTime,
    ): Boolean
}
