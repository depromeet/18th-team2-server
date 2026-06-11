package com.team2.server.party.application.port

interface RealtimePartyPresencePort {
    fun findOnlineParticipantTokens(partyId: Long): Set<String>
}
