package com.team2.server.burstgame.application.event

import java.time.LocalDateTime

data class BurstGameEndedEvent(
    val partyId: Long,
    val endedAt: LocalDateTime,
)
