package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdvancePartyPhaseUseCaseTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val phaseStore: PartyPhaseStore = mock()
    private val eventBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val fixedNow = LocalDateTime.of(2026, 5, 26, 20, 0, 5)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        AdvancePartyPhaseUseCase(partyService, participantService, phaseStore, eventBroadcaster, clock)

    @Test
    fun `호스트가 ENTRY→MUSIC 전환 성공 시 SSE 브로드캐스트`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.advance(eq(partyId), eq(PartyPhase.ENTRY), eq(PartyPhase.MUSIC), any())).thenReturn(true)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow),
        )

        val result = useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        assertEquals(PartyPhase.MUSIC, result.phase)
        verify(eventBroadcaster).broadcastPhaseChanged(eq(partyId), eq(PartyPhase.MUSIC), any(), any())
    }

    @Test
    fun `비호스트가 ENTRY→MUSIC 시도 시 403`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)

        assertFailsWith<BusinessException> {
            useCase(partyId, userId = 99L, participantToken = null, currentPhase = PartyPhase.ENTRY)
        }
        verify(phaseStore, never()).advance(any(), any(), any(), any())
    }

    @Test
    fun `CAS 실패 시 SSE 브로드캐스트 없음`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.advance(any(), any(), any(), any())).thenReturn(false)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow.minusSeconds(3)),
        )

        useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        verify(eventBroadcaster, never()).broadcastPhaseChanged(any(), any(), any(), any())
    }

    @Test
    fun `허용되지 않는 currentPhase 시 400`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)

        assertFailsWith<BusinessException> {
            useCase(partyId, userId = 10L, participantToken = null, currentPhase = PartyPhase.BURST)
        }
    }
}
