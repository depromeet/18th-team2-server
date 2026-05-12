package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyInviteService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ActivateInviteLinkUseCase(
    private val partyInviteService: PartyInviteService,
) {
    @Transactional
    fun activate(
        partyId: Long,
        userId: Long,
    ): String = partyInviteService.activateInviteLink(partyId = partyId, userId = userId)
}
