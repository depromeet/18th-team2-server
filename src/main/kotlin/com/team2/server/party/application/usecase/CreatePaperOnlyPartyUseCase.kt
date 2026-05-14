package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePaperOnlyPartyUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    fun invoke(
        userId: Long,
        command: CreatePaperOnlyPartyCommand,
    ): Long = partyService.createPaperOnlyParty(userId = userId, command = command)
}
