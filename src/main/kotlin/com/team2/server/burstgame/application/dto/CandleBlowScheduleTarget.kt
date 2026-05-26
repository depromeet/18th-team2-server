package com.team2.server.burstgame.application.dto

import java.time.LocalDateTime

data class CandleBlowScheduleTarget(
    val partyId: Long,
    val partyStartedAt: LocalDateTime,
)
