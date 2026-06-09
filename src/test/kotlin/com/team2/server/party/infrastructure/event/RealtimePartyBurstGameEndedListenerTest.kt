package com.team2.server.party.infrastructure.event

import com.team2.server.party.application.event.RealtimePartyBurstGameEndedEvent
import com.team2.server.party.application.usecase.MarkRealtimePartyBurstGameEndedUseCase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDateTime

class RealtimePartyBurstGameEndedListenerTest {
    private val useCase: MarkRealtimePartyBurstGameEndedUseCase = mock()
    private val listener = RealtimePartyBurstGameEndedListener(useCase)

    @Test
    fun `박터뜨리기 종료 이벤트를 영속화한다`() {
        val event = RealtimePartyBurstGameEndedEvent(1L, LocalDateTime.of(2026, 6, 8, 20, 0))

        listener.onBurstGameEnded(event)

        verify(useCase).invoke(event.partyId, event.endedAt)
    }
}
