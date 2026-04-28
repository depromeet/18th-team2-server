package com.team2.server.party.service

import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.entity.Party
import com.team2.server.party.repository.PartyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
) {
    @Transactional
    fun createParty(
        userId: Long,
        request: CreatePartyRequest,
    ): CreatePartyResponse {
        val startedAt = LocalDateTime.of(request.startedDate, request.startTime)
        val party =
            Party(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = startedAt,
            )
        val saved = partyRepository.save(party)
        return CreatePartyResponse(partyId = saved.id)
    }
}
