package com.team2.server.party.repository

import com.team2.server.party.entity.PartyInvite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface PartyInviteRepository : JpaRepository<PartyInvite, Long> {
    fun findByToken(token: String): PartyInvite?

    fun findByPartyIdAndExpiresAtAfter(
        partyId: Long,
        now: LocalDateTime,
    ): PartyInvite?

    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
}
