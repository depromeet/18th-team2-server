package com.team2.server.party.infrastructure.event

import com.team2.server.party.application.event.RealtimePartyBurstGameEndedEvent
import com.team2.server.party.application.usecase.MarkRealtimePartyBurstGameEndedUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RealtimePartyBurstGameEndedListener(
    private val markRealtimePartyBurstGameEndedUseCase: MarkRealtimePartyBurstGameEndedUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onBurstGameEnded(event: RealtimePartyBurstGameEndedEvent) {
        markRealtimePartyBurstGameEndedUseCase(event.partyId, event.endedAt)
    }
}
