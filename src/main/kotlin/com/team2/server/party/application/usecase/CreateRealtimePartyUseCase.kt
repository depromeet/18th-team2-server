package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.service.PartyService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateRealtimePartyUseCase(
    private val partyService: PartyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun invoke(
        userId: Long,
        command: CreateRealtimePartyCommand,
    ): Long {
        val partyId = partyService.createRealtimeParty(userId = userId, command = command)
        applicationEventPublisher.publishEvent(
            RealtimePartyCreatedEvent(
                partyId = partyId,
                startedAt = command.startedDate.atTime(command.startTime),
            ),
        )
        return partyId
    }
}
