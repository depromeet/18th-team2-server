package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun existsByPartyIdAndUserId(
        partyId: Long,
        userId: Long,
    ): Boolean
}
