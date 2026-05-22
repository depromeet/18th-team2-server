package com.team2.server.burstgame.application.port

interface BurstGameCompletionReader {
    fun isEnded(partyId: Long): Boolean
}
