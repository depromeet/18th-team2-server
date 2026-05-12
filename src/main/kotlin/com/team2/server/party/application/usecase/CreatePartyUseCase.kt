package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePartyUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    fun createRealtime(
        userId: Long,
        command: CreateRealtimePartyCommand,
    ): Long = partyService.createRealtimeParty(userId = userId, command = command)

    @Transactional
    fun createPaperOnly(
        userId: Long,
        command: CreatePaperOnlyPartyCommand,
    ): Long = partyService.createPaperOnlyParty(userId = userId, command = command)
}
