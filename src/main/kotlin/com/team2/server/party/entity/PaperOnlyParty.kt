package com.team2.server.party.entity

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "paper_only_party")
@DiscriminatorValue("PAPER_ONLY")
class PaperOnlyParty(
    ownerId: Long,
    name: String? = null,
    celebrantNickname: String? = null,
    purpose: PartyPurpose = PartyPurpose.BIRTHDAY,
    startedAt: LocalDateTime,
) : Party(ownerId, name, celebrantNickname, startedAt, purpose) {
    override val partyOption: PartyOption get() = PartyOption.PAPER_ONLY

    fun status(now: LocalDateTime = LocalDateTime.now()): PaperOnlyPartyStatus {
        val openTime = startedAt
        return when {
            isEnded(now) -> PaperOnlyPartyStatus.CLOSED
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
