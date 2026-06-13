package com.team2.server.burstgame.application.port

import java.time.LocalDateTime

interface CandleBlowStatusReader {
    fun isCandleBlowFinished(
        partyId: Long,
        hostEnteredAt: LocalDateTime?,
        now: LocalDateTime,
    ): Boolean
}
