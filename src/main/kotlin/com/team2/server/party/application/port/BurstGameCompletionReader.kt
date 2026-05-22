package com.team2.server.party.application.port

interface BurstGameCompletionReader {
    fun isEnded(partyId: Long): Boolean
}
