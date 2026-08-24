package com.team2.server.party.application.event

import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals

class RealtimePartyEndingEventPublisherTest {
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val publisher = RealtimePartyEndingEventPublisher(applicationEventPublisher)
    private val endingStartedAt = LocalDateTime.of(2026, 6, 7, 10, 0)
    private val endedAt = endingStartedAt.plusSeconds(60)

    @Test
    fun `publishes ending result display info`() {
        publisher.publish(
            RealtimePartyEndResult(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endedAt,
                endingReason = RealtimePartyEndingReason.HOST_REQUEST,
                hostNickname = "주최자",
                serverNow = endingStartedAt,
            ),
        )

        assertPublishedEvent(RealtimePartyEndingReason.HOST_REQUEST)
    }

    @Test
    fun `publishes schedule target display info`() {
        publisher.publish(
            RealtimeEndingScheduleTarget(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endedAt,
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                startedNow = true,
            ),
        )

        assertPublishedEvent(RealtimePartyEndingReason.TIME_LIMIT_REACHED)
    }

    private fun assertPublishedEvent(endingReason: RealtimePartyEndingReason) {
        val captor = argumentCaptor<RealtimePartyEndingStartedEvent>()
        verify(applicationEventPublisher).publishEvent(captor.capture())
        assertEquals(1L, captor.firstValue.partyId)
        assertEquals(endingStartedAt, captor.firstValue.endingStartedAt)
        assertEquals(endedAt, captor.firstValue.endedAt)
        assertEquals(endingReason, captor.firstValue.endingReason)
        assertEquals("주최자", captor.firstValue.hostNickname)
    }
}
