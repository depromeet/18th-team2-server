package com.team2.server.burstgame.application.service

import java.time.LocalDateTime

interface BurstGameEndScheduler {
    fun schedule(
        partyId: Long,
        endsAt: LocalDateTime,
        onEnd: (Long) -> Unit,
    )

    fun cancel(partyId: Long): Boolean
}
