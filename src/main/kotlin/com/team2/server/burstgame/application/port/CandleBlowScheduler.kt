package com.team2.server.burstgame.application.port

import java.time.LocalDateTime

interface CandleBlowScheduler {
    fun scheduleStart(
        partyId: Long,
        startsAt: LocalDateTime,
        onStart: (Long) -> Unit,
    )

    fun scheduleEnd(
        partyId: Long,
        endsAt: LocalDateTime,
        onEnd: (Long) -> Unit,
    )

    fun cancel(partyId: Long): Boolean
}
