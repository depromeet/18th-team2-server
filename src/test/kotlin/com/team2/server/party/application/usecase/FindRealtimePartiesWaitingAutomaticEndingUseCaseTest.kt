package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FindRealtimePartiesWaitingAutomaticEndingUseCaseTest {
    private val partyService: PartyService = mock()
    private val useCase = FindRealtimePartiesWaitingAutomaticEndingUseCase(partyService)

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
        whenever(partyService.findRealtimePartiesWaitingAutomaticEnding(startedAfter)).thenReturn(listOf(party))

        val result = useCase(startedAfter)

        assertEquals(listOf(party), result)
        verify(partyService).findRealtimePartiesWaitingAutomaticEnding(startedAfter)
    }
}
