package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.party.application.dto.RealtimePartyHostEnteredScheduleData
import com.team2.server.party.application.usecase.FindRealtimePartiesWithHostEnteredUseCase
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
    private val findRealtimePartiesWithHostEnteredUseCase: FindRealtimePartiesWithHostEnteredUseCase = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-24T11:01:20Z"), ZoneId.of("Asia/Seoul"))
    private val candleBlowProperties = CandleBlowProperties(durationSeconds = 300L)
    private val useCase =
        RecoverCandleBlowScheduleUseCase(
            findRealtimePartiesWithHostEnteredUseCase = findRealtimePartiesWithHostEnteredUseCase,
            candleBlowProperties = candleBlowProperties,
            clock = clock,
        )

    @Test
    fun `촛불끄기 복구 대상 파티를 스케줄 타겟으로 변환한다`() {
        val now = LocalDateTime.ofInstant(clock.instant(), clock.zone)
        val hostEnteredAfter =
            now.minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + candleBlowProperties.durationSeconds)
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        val party = RealtimePartyHostEnteredScheduleData(partyId = 10L, hostEnteredAt = hostEnteredAt)
        whenever(findRealtimePartiesWithHostEnteredUseCase(hostEnteredAfter)).thenReturn(listOf(party))

        val targets = useCase()

        assertEquals(1, targets.size)
        assertEquals(party.partyId, targets[0].partyId)
        assertEquals(hostEnteredAt, targets[0].hostEnteredAt)
        verify(findRealtimePartiesWithHostEnteredUseCase).invoke(hostEnteredAfter)
    }
}
