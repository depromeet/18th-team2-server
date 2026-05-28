package com.team2.server.chat.application.port

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.party.domain.entity.RealtimeParty
import java.time.LocalDateTime

interface RealtimePartyEntryProfilePort {
    fun resolve(
        party: RealtimeParty,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        now: LocalDateTime,
    ): RealtimePartyEntryProfileResult
}

data class RealtimePartyEntryProfileResult(
    val participantToken: String,
    val isCelebrant: Boolean,
    val nickname: String,
    val characterId: Long?,
)
