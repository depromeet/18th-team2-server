package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyScheduleData
import com.team2.server.party.application.service.PartyQueryService
import com.team2.server.party.domain.entity.RealtimeParty
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FindRealtimePartiesWaitingAutomaticEndingUseCaseTest {
    private val partyQueryService: PartyQueryService = mock()
    private val useCase = FindRealtimePartiesWaitingAutomaticEndingUseCase(partyQueryService)

    @Test
    fun `자동 종료 대기 중인 실시간 파티를 조회한다`() {
        val startedAfter = LocalDateTime.of(2026, 5, 24, 20, 0)
        val party =
            RealtimeParty(
                ownerId = 1L,
                name = "실시간 파티",
                celebrantNickname = "주인공",
                startedAt = startedAfter.plusMinutes(1),
            )
        whenever(partyQueryService.findRealtimePartiesWaitingAutomaticEnding(startedAfter)).thenReturn(listOf(party))

        val result = useCase(startedAfter)

        assertEquals(listOf(RealtimePartyScheduleData.from(party)), result)
        verify(partyQueryService).findRealtimePartiesWaitingAutomaticEnding(startedAfter)
    }
}
