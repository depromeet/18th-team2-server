package com.team2.server.chat.dto

import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

data class PartyPhaseChangedEventPayload(
    val partyId: Long,
    val phase: PartyPhase,
    val phaseStartedAt: LocalDateTime,
    val serverNow: LocalDateTime,
)
