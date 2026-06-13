package com.team2.server.chat.application.dto

import com.team2.server.party.application.dto.RealtimePartyStateResult
import java.time.LocalDateTime

data class EnterRealtimePartyResult(
    val participantToken: String,
    val partyId: Long,
    val startedAt: LocalDateTime,
    val isCelebrant: Boolean,
    val nickname: String,
    val characterId: Long?,
    val partyState: RealtimePartyStateResult,
)
