package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    fun findByParticipantToken(participantToken: String): RealtimeParticipantProfile?

    @Modifying
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
