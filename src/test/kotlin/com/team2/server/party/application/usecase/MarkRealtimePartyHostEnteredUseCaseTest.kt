package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkRealtimePartyHostEnteredUseCaseTest {
    private val partyService: PartyService = mock()
    private val useCase = MarkRealtimePartyHostEnteredUseCase(partyService)

    @Test
    fun `주최자 입장 시각을 처음 저장하면 반환한다`() {
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        whenever(partyService.markHostEnteredIfAbsent(1L, hostEnteredAt)).thenReturn(true)

        val result = useCase(partyId = 1L, hostEnteredAt = hostEnteredAt)

        assertEquals(hostEnteredAt, result)
    }

    @Test
    fun `이미 저장되어 있으면 null을 반환한다`() {
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        whenever(partyService.markHostEnteredIfAbsent(1L, hostEnteredAt)).thenReturn(false)

        val result = useCase(partyId = 1L, hostEnteredAt = hostEnteredAt)

        assertNull(result)
    }
}
