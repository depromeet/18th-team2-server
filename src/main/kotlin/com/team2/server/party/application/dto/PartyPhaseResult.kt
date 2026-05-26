package com.team2.server.party.application.dto

import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

data class PartyPhaseResult(
    val partyId: Long,
    val phase: PartyPhase,
    val phaseStartedAt: LocalDateTime,
    val serverNow: LocalDateTime,
)
