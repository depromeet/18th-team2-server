package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertNull
import kotlin.test.assertSame

class StartAutomaticRealtimePartyEndUseCaseTest {
    private val realtimePartyEndService: RealtimePartyEndService = mock()
    private val eventPublisher: RealtimePartyEndingEventPublisher = mock()
    private val useCase = StartAutomaticRealtimePartyEndUseCase(realtimePartyEndService, eventPublisher)
    private val endingStartedAt = LocalDateTime.of(2026, 6, 7, 10, 0)

    @Test
    fun `returns null without publishing when ending target is absent`() {
        whenever(realtimePartyEndService.startIfNotStartedOrNull(1L, endingStartedAt)).thenReturn(null)

        val result = useCase(1L, endingStartedAt)

        assertNull(result)
        verify(eventPublisher, never()).publish(org.mockito.kotlin.any<RealtimeEndingScheduleTarget>())
    }

    @Test
    fun `publishes when automatic ending starts now`() {
        val target = target(startedNow = true)
        whenever(realtimePartyEndService.startIfNotStartedOrNull(1L, endingStartedAt)).thenReturn(target)

        val result = useCase(1L, endingStartedAt)

        assertSame(target, result)
        verify(eventPublisher).publish(target)
    }

    @Test
    fun `does not publish when automatic ending already started`() {
        val target = target(startedNow = false)
        whenever(realtimePartyEndService.startIfNotStartedOrNull(1L, endingStartedAt)).thenReturn(target)

        val result = useCase(1L, endingStartedAt)

        assertSame(target, result)
        verify(eventPublisher, never()).publish(target)
    }

    private fun target(startedNow: Boolean) =
        RealtimeEndingScheduleTarget(
            partyId = 1L,
            endingStartedAt = endingStartedAt,
            endedAt = endingStartedAt.plusSeconds(60),
            endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
            hostNickname = "주최자",
            startedNow = startedNow,
        )
}
