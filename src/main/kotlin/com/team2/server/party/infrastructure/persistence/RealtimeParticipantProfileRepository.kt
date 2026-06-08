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

    @Query(
        """
        SELECT (COUNT(profile) > 0)
        FROM RealtimeParticipantProfile profile
        WHERE profile.participant.party.id = :partyId
          AND LOWER(profile.nickname) = LOWER(:nickname)
          AND profile.participant.id <> :excludingParticipantId
        """,
    )
    fun existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
        partyId: Long,
        nickname: String,
        excludingParticipantId: Long,
    ): Boolean

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
          AND participant.hasLeft = false
        ORDER BY participant.id ASC
        """,
    )
    fun findAllByPartyIdOrderByParticipantIdAsc(partyId: Long): List<RealtimeParticipantProfile>
}
