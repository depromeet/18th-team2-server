package com.team2.server.burstgame.application.port

interface CandleBlowStatusReader {
    fun isCandleBlowCompleted(partyId: Long): Boolean
}
