package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PartyQueryService(
    private val partyRepository: PartyRepository,
) {
    /** 파티 종류와 무관하게 파티를 조회한다. 없으면 PARTY_NOT_FOUND. */
    fun requireParty(partyId: Long): Party = findParty(partyId)

    fun requireRealtimeParty(partyId: Long): RealtimeParty {
        val party = findParty(partyId)
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }

    fun findRealtimePartiesWaitingAutomaticEnding(startedAfter: LocalDateTime): List<RealtimeParty> =
        partyRepository.findRealtimePartiesWaitingAutomaticEnding(startedAfter)

    private fun findParty(partyId: Long) =
        partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
}
