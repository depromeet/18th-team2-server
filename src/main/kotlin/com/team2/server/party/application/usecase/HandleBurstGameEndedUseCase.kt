package com.team2.server.party.application.usecase

import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class HandleBurstGameEndedUseCase(
    private val partyRepository: PartyRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(partyId: Long): Boolean {
        val realtimeParty = findRealtimeParty(partyId)
        return realtimeParty?.canNotifyHostEndAvailable(LocalDateTime.now(clock)) == true
    }

    private fun findRealtimeParty(partyId: Long): RealtimeParty? {
        val party = partyRepository.findPartyById(partyId)
        return when {
            party == null -> null
            party.partyOption != PartyOption.REALTIME -> null
            else -> Hibernate.unproxy(party) as RealtimeParty
        }
    }

    private fun RealtimeParty.canNotifyHostEndAvailable(now: LocalDateTime): Boolean =
        liveEndingStartedAt == null && status(now) == RealtimePartyStatus.LIVE_OPEN
}
