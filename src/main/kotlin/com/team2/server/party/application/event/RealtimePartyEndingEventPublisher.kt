package com.team2.server.party.application.event

import com.team2.server.party.application.dto.RealtimePartyEndResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEndingEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun publish(result: RealtimePartyEndResult) {
        publish(
            partyId = result.partyId,
            endingStartedAt = result.endingStartedAt,
            endedAt = result.endedAt,
        )
    }

    fun publish(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
    ) {
        applicationEventPublisher.publishEvent(
            RealtimePartyEndingStartedEvent(
                partyId = partyId,
                endingStartedAt = endingStartedAt,
                endedAt = endedAt,
            ),
        )
    }
}
