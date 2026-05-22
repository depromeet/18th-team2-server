package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.service.RealtimePartyEndService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StartAutomaticRealtimePartyEndUseCase(
    private val realtimePartyEndService: RealtimePartyEndService,
    private val realtimePartyEndingEventPublisher: RealtimePartyEndingEventPublisher,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ): RealtimeEndingScheduleTarget? {
        val target = realtimePartyEndService.startIfNotStartedOrNull(partyId, endingStartedAt) ?: return null
        if (target.startedNow) {
            realtimePartyEndingEventPublisher.publish(target.partyId, target.endingStartedAt, target.endedAt)
        }
        return RealtimeEndingScheduleTarget(
            partyId = target.partyId,
            endingStartedAt = target.endingStartedAt,
            endedAt = target.endedAt,
            startedNow = target.startedNow,
        )
    }
}
