package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeletePartyUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    fun delete(
        partyId: Long,
        userId: Long,
    ) {
        partyService.deleteParty(partyId = partyId, userId = userId)
    }
}
