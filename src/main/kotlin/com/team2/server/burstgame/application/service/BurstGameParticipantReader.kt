package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.domain.BurstGameParticipantInfo

interface BurstGameParticipantReader {
    fun resolve(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): BurstGameParticipantInfo
}
