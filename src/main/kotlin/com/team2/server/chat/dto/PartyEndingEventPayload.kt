package com.team2.server.chat.dto

import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import java.time.LocalDateTime

data class PartyEndingEventPayload(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val endingReason: RealtimePartyEndingReason,
    val hostNickname: String,
    val serverNow: LocalDateTime,
)
