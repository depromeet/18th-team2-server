package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    @EntityGraph(attributePaths = ["participant", "participant.party"])
    fun findByParticipantToken(participantToken: String): RealtimeParticipantProfile?

    @Modifying
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)

    @Query(
        """
        SELECT rpp
        FROM RealtimeParticipantProfile rpp
        JOIN FETCH rpp.participant participant
        LEFT JOIN FETCH participant.user
        LEFT JOIN FETCH rpp.character
        WHERE participant.party.id = :partyId
        ORDER BY participant.id ASC
        """,
    )
    fun findAllByPartyIdOrderByParticipantIdAsc(partyId: Long): List<RealtimeParticipantProfile>
}
