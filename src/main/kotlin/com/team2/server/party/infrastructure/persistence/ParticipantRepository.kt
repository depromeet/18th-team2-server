package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.user.entity.User
import org.springframework.data.domain.Pageable
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
          AND party.startedAt > :startedAfter
        ORDER BY party.startedAt ASC, participant.createdAt DESC, participant.id DESC
        """,
    )
    fun findNotEndedByUserId(
        userId: Long,
        startedAfter: LocalDateTime,
    ): List<Participant>

    @Query(
        """
        SELECT p
        FROM Participant p
        JOIN FETCH p.party party
        WHERE p.user.id = :userId
          AND (:cursor IS NULL OR p.id < :cursor)
        ORDER BY p.id DESC
        """,
    )
    fun findArchiveByUserId(
        userId: Long,
        cursor: Long?,
        pageable: Pageable,
    ): List<Participant>

    @Query("SELECT COUNT(p) FROM Participant p WHERE p.user.id = :userId")
    fun countArchiveByUserId(userId: Long): Long
}
