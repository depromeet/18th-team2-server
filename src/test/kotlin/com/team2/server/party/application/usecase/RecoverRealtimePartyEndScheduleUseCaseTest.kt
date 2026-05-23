package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimeHostEndAvailableSchedule
import com.team2.server.party.application.dto.RealtimePartyEndRecoverySchedules
import com.team2.server.party.application.service.RealtimePartyEndService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class RecoverRealtimePartyEndScheduleUseCaseTest {
    private val realtimePartyEndService: RealtimePartyEndService = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase = RecoverRealtimePartyEndScheduleUseCase(realtimePartyEndService, clock)

    @Test
    fun `starts due automatic endings and maps recovery schedules`() {
        whenever(realtimePartyEndService.findRecoverySchedules(now))
            .thenReturn(
                RealtimePartyEndRecoverySchedules(
                    hostEndAvailableSchedules = listOf(RealtimeHostEndAvailableSchedule(3L, now.minusMinutes(1))),
                    automaticEndSchedules = listOf(RealtimeAutomaticEndSchedule(1L, now.plusMinutes(1))),
                ),
            )
        whenever(realtimePartyEndService.findEndingTargets(now))
            .thenReturn(listOf(RealtimeEndingScheduleTarget(2L, now.minusMinutes(1), now, startedNow = true)))

        val result = useCase()

        verify(realtimePartyEndService).startDueAutomaticEndings(now)
        assertEquals(3L, result.hostEndAvailableSchedules.single().partyId)
        assertEquals(now.minusMinutes(1), result.hostEndAvailableSchedules.single().startedAt)
        assertEquals(1L, result.automaticEndSchedules.single().partyId)
        assertEquals(now.plusMinutes(1), result.automaticEndSchedules.single().endingStartedAt)
        assertEquals(2L, result.endingTargets.single().partyId)
        assertEquals(now.minusMinutes(1), result.endingTargets.single().endingStartedAt)
        assertEquals(now, result.endingTargets.single().endedAt)
        assertEquals(true, result.endingTargets.single().startedNow)
    }
}
