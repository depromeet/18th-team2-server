package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.event.BurstGameEndedEvent
import com.team2.server.party.application.event.RealtimePartyBurstGameEndedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BurstGameEndedPartyEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameEnded(event: BurstGameEndedEvent) {
        applicationEventPublisher.publishEvent(
            RealtimePartyBurstGameEndedEvent(
                partyId = event.partyId,
                endedAt = event.endedAt,
            ),
        )
    }
}
