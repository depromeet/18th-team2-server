package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.RealtimePartyEndService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleBurstGameEndedUseCaseTest {
    private val realtimePartyEndService: RealtimePartyEndService = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        HandleBurstGameEndedUseCase(
            realtimePartyEndService = realtimePartyEndService,
            clock = clock,
        )

    @Test
    fun `LIVE_OPEN realtime party marks burst game ended and returns true`() {
        whenever(realtimePartyEndService.canNotifyHostEndAvailable(1L, now)).thenReturn(true)

        val result = useCase(1L)

        assertTrue(result)
    }

    @Test
    fun `non realtime party returns false`() {
        whenever(realtimePartyEndService.canNotifyHostEndAvailable(1L, now)).thenReturn(false)

        val result = useCase(1L)

        assertFalse(result)
    }

    @Test
    fun `LIVE_ENDING party returns false`() {
        whenever(realtimePartyEndService.canNotifyHostEndAvailable(1L, now)).thenReturn(false)

        val result = useCase(1L)

        assertFalse(result)
    }

    @Test
    fun `missing party returns false`() {
        whenever(realtimePartyEndService.canNotifyHostEndAvailable(1L, now)).thenReturn(false)

        val result = useCase(1L)

        assertFalse(result)
    }
}
