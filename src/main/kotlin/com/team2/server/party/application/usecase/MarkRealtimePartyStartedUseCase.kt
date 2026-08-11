package com.team2.server.party.application.usecase

import com.team2.server.party.application.event.RealtimePartyStartedEvent
import com.team2.server.party.application.service.PartyService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkRealtimePartyStartedUseCase(
    private val partyService: PartyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): LocalDateTime? {
        if (!partyService.markLiveStartedIfAbsent(partyId, liveStartedAt)) return null
        applicationEventPublisher.publishEvent(RealtimePartyStartedEvent(partyId, liveStartedAt))
        return liveStartedAt
    }
}
