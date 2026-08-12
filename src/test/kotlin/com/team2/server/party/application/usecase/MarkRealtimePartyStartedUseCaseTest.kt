package com.team2.server.party.application.usecase

import com.team2.server.party.application.event.RealtimePartyStartedEvent
import com.team2.server.party.application.service.PartyService
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkRealtimePartyStartedUseCaseTest {
    private val partyService: PartyService = mock()
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val useCase = MarkRealtimePartyStartedUseCase(partyService, applicationEventPublisher)

    @Test
    fun `파티 시작 시각을 처음 저장하면 반환한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(true)

        val result = useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        assertEquals(liveStartedAt, result)
    }

    @Test
    fun `이미 저장되어 있으면 null을 반환한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(false)

        val result = useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        assertNull(result)
    }

    @Test
    fun `처음 저장하면 시작 이벤트를 발행한다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(true)

        useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        verify(applicationEventPublisher).publishEvent(RealtimePartyStartedEvent(1L, liveStartedAt))
    }

    @Test
    fun `이미 저장되어 있으면 이벤트를 발행하지 않는다`() {
        val liveStartedAt = LocalDateTime.of(2026, 5, 24, 20, 3)
        whenever(partyService.markLiveStartedIfAbsent(1L, liveStartedAt)).thenReturn(false)

        useCase(partyId = 1L, liveStartedAt = liveStartedAt)

        verify(applicationEventPublisher, never()).publishEvent(any<RealtimePartyStartedEvent>())
    }
}
