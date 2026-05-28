package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyHostEnteredScheduleData
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FindRealtimePartiesWithHostEnteredUseCaseTest {
    private val partyService: PartyService = mock()
    private val useCase = FindRealtimePartiesWithHostEnteredUseCase(partyService)

    @Test
    fun `주최자 입장 시각이 있는 실시간 파티를 스케줄 데이터로 변환한다`() {
        val hostEnteredAfter = LocalDateTime.of(2026, 5, 24, 19, 55)
        val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
        val party =
            RealtimeParty(
                ownerId = 1L,
                startedAt = hostEnteredAt.minusMinutes(1),
                hostEnteredAt = hostEnteredAt,
            )
        whenever(partyService.findRealtimePartiesWithHostEnteredAfter(hostEnteredAfter))
            .thenReturn(listOf(party))

        val result = useCase(hostEnteredAfter)

        assertEquals(listOf(RealtimePartyHostEnteredScheduleData.from(party)), result)
        verify(partyService).findRealtimePartiesWithHostEnteredAfter(hostEnteredAfter)
    }
}
