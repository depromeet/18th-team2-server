package com.team2.server.party.application.usecase

import com.team2.server.party.application.event.RealtimePartyHostEnteredEventPublisher
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkRealtimePartyHostEnteredUseCase(
    private val partyService: PartyService,
    private val eventPublisher: RealtimePartyHostEnteredEventPublisher,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        hostEnteredAt: LocalDateTime,
    ): LocalDateTime? {
        if (!partyService.markHostEnteredIfAbsent(partyId, hostEnteredAt)) return null
        eventPublisher.publish(partyId = partyId, hostEnteredAt = hostEnteredAt)
        return hostEnteredAt
    }
}
