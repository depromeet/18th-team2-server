package com.team2.server.party.application.usecase

import com.team2.server.party.application.event.RealtimePartyHostEnteredEventPublisher
import com.team2.server.party.application.service.PartyService
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkRealtimePartyHostEnteredUseCaseTest {
    private val partyService: PartyService = mock()
    private val eventPublisher: RealtimePartyHostEnteredEventPublisher = mock()
    private val useCase = MarkRealtimePartyHostEnteredUseCase(partyService, eventPublisher)

    @Test
    fun `주최자 입장 시각을 처음 저장하면 이벤트를 발행한다`() {
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        whenever(partyService.markHostEnteredIfAbsent(1L, hostEnteredAt)).thenReturn(true)

        val result = useCase(partyId = 1L, hostEnteredAt = hostEnteredAt)

        assertEquals(hostEnteredAt, result)
        verify(eventPublisher).publish(partyId = 1L, hostEnteredAt = hostEnteredAt)
    }

    @Test
    fun `이미 저장되어 있으면 이벤트를 발행하지 않는다`() {
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        whenever(partyService.markHostEnteredIfAbsent(1L, hostEnteredAt)).thenReturn(false)

        val result = useCase(partyId = 1L, hostEnteredAt = hostEnteredAt)

        assertNull(result)
        verify(eventPublisher, never()).publish(partyId = 1L, hostEnteredAt = hostEnteredAt)
    }
}
