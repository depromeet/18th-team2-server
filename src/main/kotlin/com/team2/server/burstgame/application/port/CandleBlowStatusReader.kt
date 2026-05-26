package com.team2.server.burstgame.application.port

interface CandleBlowStatusReader {
    fun isCandleBlowFinished(partyId: Long): Boolean
}
