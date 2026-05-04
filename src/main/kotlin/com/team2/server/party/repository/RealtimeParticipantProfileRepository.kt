package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.JpaRepository

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
