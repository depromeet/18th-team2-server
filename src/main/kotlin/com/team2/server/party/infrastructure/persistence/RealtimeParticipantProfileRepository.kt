package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    fun findByParticipantToken(participantToken: String): RealtimeParticipantProfile?

    @Query(
        """
        SELECT profile
        FROM RealtimeParticipantProfile profile
        JOIN FETCH profile.participant participant
        WHERE participant.party.id = :partyId
        ORDER BY profile.id ASC
        """,
    )
    fun findAllByPartyIdOrderByIdAsc(partyId: Long): List<RealtimeParticipantProfile>

    fun findAllByParticipantIdIn(participantIds: Collection<Long>): List<RealtimeParticipantProfile>

    @Modifying
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
