package com.team2.server.party.application.port

interface BurstGameStartPort {
    fun start(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    )
}
