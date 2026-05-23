package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.event.BurstGameEndedEvent
import com.team2.server.party.application.event.RealtimePartyBurstGameEndedEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class BurstGameEndedPartyEventPublisherTest {
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val publisher = BurstGameEndedPartyEventPublisher(applicationEventPublisher)

    @Test
    fun `publishes party burst game ended event`() {
        val endedAt = LocalDateTime.of(2026, 5, 23, 14, 30)

        publisher.onBurstGameEnded(BurstGameEndedEvent(partyId = 1L, endedAt = endedAt))

        verify(applicationEventPublisher).publishEvent(
            RealtimePartyBurstGameEndedEvent(partyId = 1L, endedAt = endedAt),
        )
    }
}
