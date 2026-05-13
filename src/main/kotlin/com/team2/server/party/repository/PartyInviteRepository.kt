package com.team2.server.party.repository

import com.team2.server.party.entity.PartyInvite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface PartyInviteRepository : JpaRepository<PartyInvite, Long> {
    fun findByToken(token: String): PartyInvite?

    fun findByPartyIdAndExpiresAtAfter(
        partyId: Long,
        now: LocalDateTime,
    ): PartyInvite?

    @Query(
        """
        SELECT invite
        FROM PartyInvite invite
        JOIN FETCH invite.party party
        WHERE party.id IN :partyIds
          AND invite.expiresAt > :now
        ORDER BY invite.expiresAt DESC, invite.createdAt DESC, invite.id DESC
        """,
    )
    fun findAllByPartyIdInAndExpiresAtAfter(
        partyIds: List<Long>,
        now: LocalDateTime,
    ): List<PartyInvite>

    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
}
