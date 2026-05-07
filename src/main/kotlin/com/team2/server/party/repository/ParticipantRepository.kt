package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByPartyAndUser(
        party: Party,
        user: User,
    ): Participant?

    fun existsByPartyAndUser(
        party: Party,
        user: User,
    ): Boolean

    fun existsByPartyIdAndUserId(
        partyId: Long,
        userId: Long,
    ): Boolean

    fun findAllByPartyId(partyId: Long): List<Participant>

    fun findByPartyIdAndUserId(
        partyId: Long,
        userId: Long,
    ): Participant?

    @Query(
        """
        SELECT participant
        FROM Participant participant
        JOIN FETCH participant.party party
        WHERE participant.user.id = :userId
          AND party.createdAt > :endedAfter
        ORDER BY party.startedAt ASC, party.id ASC
        """,
    )
    fun findUpcomingByUserId(
        userId: Long,
        endedAfter: LocalDateTime,
    ): List<Participant>
}
