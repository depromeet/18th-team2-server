package com.team2.server.burstgame.application.service

interface CandleBlowStatusReader {
    fun isCandleBlowCompleted(partyId: Long): Boolean
}
