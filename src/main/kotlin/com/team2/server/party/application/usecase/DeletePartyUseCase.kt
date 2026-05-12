package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
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
