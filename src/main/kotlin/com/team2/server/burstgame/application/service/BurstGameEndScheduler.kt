package com.team2.server.burstgame.application.service

import java.time.LocalDateTime

interface BurstGameEndScheduler {
    fun schedule(
        roundId: String,
        endsAt: LocalDateTime,
    )
}
