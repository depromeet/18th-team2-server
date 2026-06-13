package com.team2.server.party.application.event

import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
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
            endingReason = result.endingReason,
            hostNickname = result.hostNickname,
        )
    }

    fun publish(target: RealtimeEndingScheduleTarget) {
        publish(
            partyId = target.partyId,
            endingStartedAt = target.endingStartedAt,
            endedAt = target.endedAt,
            endingReason = target.endingReason,
            hostNickname = target.hostNickname,
        )
    }

    private fun publish(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
        hostNickname: String,
    ) {
        applicationEventPublisher.publishEvent(
            RealtimePartyEndingStartedEvent(
                partyId = partyId,
                endingStartedAt = endingStartedAt,
                endedAt = endedAt,
                endingReason = endingReason,
                hostNickname = hostNickname,
            ),
        )
    }
}
