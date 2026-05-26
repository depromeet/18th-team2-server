package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.event.BurstGameStartedEvent
import com.team2.server.party.application.event.RealtimePartyBurstGameStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BurstGameStartedPartyEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameStarted(event: BurstGameStartedEvent) {
        applicationEventPublisher.publishEvent(
            RealtimePartyBurstGameStartedEvent(partyId = event.partyId, startedAt = event.startedAt),
        )
    }
}
