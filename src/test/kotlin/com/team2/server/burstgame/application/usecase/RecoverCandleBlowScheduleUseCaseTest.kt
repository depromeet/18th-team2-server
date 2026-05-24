package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoverCandleBlowScheduleUseCaseTest {
    private val partyService: PartyService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-24T11:01:20Z"), ZoneId.of("Asia/Seoul"))
    private val useCase =
        RecoverCandleBlowScheduleUseCase(
            partyService = partyService,
            clock = clock,
        )

    @Test
    fun `촛불끄기 복구 대상 파티를 스케줄 타겟으로 변환한다`() {
        val now = LocalDateTime.ofInstant(clock.instant(), clock.zone)
        val startedAfter = now.minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + CandleBlowPolicy.DURATION_SECONDS)
        val partyStartedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        val party =
            RealtimeParty(
                ownerId = 1L,
                name = "실시간 파티",
                celebrantNickname = "주인공",
                startedAt = partyStartedAt,
            )
        whenever(partyService.findRealtimePartiesWaitingAutomaticEnding(startedAfter)).thenReturn(listOf(party))

        val targets = useCase()

        assertEquals(1, targets.size)
        assertEquals(0L, targets[0].partyId)
        assertEquals(partyStartedAt, targets[0].partyStartedAt)
        verify(partyService).findRealtimePartiesWaitingAutomaticEnding(startedAfter)
    }
}
