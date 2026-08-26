package com.team2.server.chat.dto

import java.time.LocalDateTime

data class PartyEndedEventPayload(
    val partyId: Long,
    val endedAt: LocalDateTime,
    val hostNickname: String,
    val serverNow: LocalDateTime,
)
