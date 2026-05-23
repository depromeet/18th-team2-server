package com.team2.server.party.application.port

import java.time.LocalDateTime

interface BurstGameCompletionReader {
    fun isCompleted(
        partyId: Long,
        now: LocalDateTime,
    ): Boolean
}
