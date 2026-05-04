package com.team2.server.party.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "paper_only_party")
class PaperOnlyParty(
    ownerId: Long,
    name: String? = null,
    celebrantNickname: String? = null,
    purpose: PartyPurpose = PartyPurpose.BIRTHDAY,
    startedAt: LocalDateTime,
) : Party(ownerId, name, celebrantNickname, startedAt, purpose, PartyOption.PAPER_ONLY) {
    fun status(now: LocalDateTime = LocalDateTime.now()): PaperOnlyPartyStatus {
        val openTime = startedAt
        val closeTime = createdAt.plusDays(Party.ENDED_AFTER_DAYS)
        return when {
            now >= closeTime -> PaperOnlyPartyStatus.CLOSED
            now >= openTime -> PaperOnlyPartyStatus.OPEN
            else -> PaperOnlyPartyStatus.READY
        }
    }
}

enum class PaperOnlyPartyStatus {
    READY,
    OPEN,
    CLOSED,
}
