package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.RealtimePartyBurstGameEndRecorder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDateTime

class MarkRealtimePartyBurstGameEndedUseCaseTest {
    private val recorder: RealtimePartyBurstGameEndRecorder = mock()
    private val useCase = MarkRealtimePartyBurstGameEndedUseCase(recorder)

    @Test
    fun `박터뜨리기 종료 시각을 최초 한 번 저장한다`() {
        val endedAt = LocalDateTime.of(2026, 6, 8, 20, 0)

        useCase(partyId = 1L, endedAt = endedAt)

        verify(recorder).recordFirst(partyId = 1L, endedAt = endedAt)
    }
}
