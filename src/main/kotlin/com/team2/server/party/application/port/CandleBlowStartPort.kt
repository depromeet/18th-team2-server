package com.team2.server.party.application.port

import java.time.LocalDateTime

interface CandleBlowStartPort {
    fun start(
        partyId: Long,
        startedAt: LocalDateTime,
    )
}
