package com.team2.server.party.application.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyHostEnteredEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun publish(
        partyId: Long,
        hostEnteredAt: LocalDateTime,
    ) {
        applicationEventPublisher.publishEvent(
            RealtimePartyHostEnteredEvent(
                partyId = partyId,
                hostEnteredAt = hostEnteredAt,
            ),
        )
    }
}
