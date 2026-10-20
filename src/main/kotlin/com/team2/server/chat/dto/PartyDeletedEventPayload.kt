package com.team2.server.chat.dto

import com.team2.server.party.domain.vo.PartyDeletedReason
import java.time.LocalDateTime

data class PartyDeletedEventPayload(
    val partyId: Long,
    val reason: PartyDeletedReason,
    val serverNow: LocalDateTime,
)
