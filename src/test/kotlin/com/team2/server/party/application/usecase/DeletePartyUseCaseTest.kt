package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyDeletedReason
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class DeletePartyUseCaseTest {
    private val now = LocalDateTime.of(2026, 10, 19, 20, 0)
    private val clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    private val partyService: PartyService = mock()
    private val socketBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val sseBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val useCase = DeletePartyUseCase(partyService, listOf(socketBroadcaster, sseBroadcaster), clock)

    @Test
    fun `실시간 파티를 삭제하면 모든 채널에 party-deleted 를 전파한다`() {
        whenever(partyService.deleteParty(partyId = 1L, userId = 42L))
            .thenReturn(RealtimeParty(ownerId = 42L, startedAt = now.plusHours(1)))

        useCase.delete(partyId = 1L, userId = 42L)

        verify(partyService).deleteParty(partyId = 1L, userId = 42L)
        verify(socketBroadcaster).broadcastPartyDeleted(1L, PartyDeletedReason.HOST_DELETED, now)
        verify(sseBroadcaster).broadcastPartyDeleted(1L, PartyDeletedReason.HOST_DELETED, now)
    }

    @Test
    fun `롤링페이퍼 전용 파티 삭제는 전파하지 않는다`() {
        whenever(partyService.deleteParty(partyId = 2L, userId = 42L))
            .thenReturn(PaperOnlyParty(ownerId = 42L, startedAt = now.plusHours(1)))

        useCase.delete(partyId = 2L, userId = 42L)

        verify(socketBroadcaster, never()).broadcastPartyDeleted(any(), any(), any())
    }
}
