package com.team2.server.burstgame.application.event

import java.time.LocalDateTime

data class BurstGameStartedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)
