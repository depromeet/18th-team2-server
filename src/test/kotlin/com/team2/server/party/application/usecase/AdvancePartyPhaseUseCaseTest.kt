package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyPhaseTransitionService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
    private val phaseTransitionService: PartyPhaseTransitionService = mock()
    private val fixedNow = LocalDateTime.of(2026, 5, 26, 20, 0, 5)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        AdvancePartyPhaseUseCase(
            partyService,
            participantService,
            phaseTransitionService,
            clock,
        )

    @Test
    fun `호스트가 ENTRY→MUSIC 전환 성공 시 SSE 브로드캐스트`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        wheneverTransitionSucceeds(partyId, PartyPhase.ENTRY, PartyPhase.MUSIC)

        val result = useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        assertEquals(PartyPhase.MUSIC, result.phase)
        verify(phaseTransitionService).advance(partyId, PartyPhase.ENTRY, PartyPhase.MUSIC, fixedNow, ownerId, null)
    }

    @Test
    fun `비호스트가 ENTRY→MUSIC 시도 시 403`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)

        assertFailsWith<BusinessException> {
            useCase(partyId, userId = 99L, participantToken = null, currentPhase = PartyPhase.ENTRY)
        }
        verify(phaseTransitionService, never()).advance(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `CAS 실패 시 SSE 브로드캐스트 없음`() {
        val partyId = 1L
        val ownerId = 10L
        val party = RealtimeParty(ownerId = ownerId, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseTransitionService.advance(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(phaseTransitionService.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, fixedNow.minusSeconds(3)),
        )

        useCase(partyId, userId = ownerId, participantToken = null, currentPhase = PartyPhase.ENTRY)

        verify(phaseTransitionService).getEntry(partyId)
    }

    @Test
    fun `파티 멤버가 MUSIC→CANDLE 전환 성공 시 촛불끄기 세션을 시작한다`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        wheneverTransitionSucceeds(partyId, PartyPhase.MUSIC, PartyPhase.CANDLE)

        val result = useCase(partyId, userId = 99L, participantToken = null, currentPhase = PartyPhase.MUSIC)

        assertEquals(PartyPhase.CANDLE, result.phase)
        verify(participantService).validatePartyMember(party, 99L, null)
        verify(phaseTransitionService).advance(partyId, PartyPhase.MUSIC, PartyPhase.CANDLE, fixedNow, 99L, null)
    }

    @Test
    fun `파티 멤버가 CANDLE→BURST 전환 성공 시 SSE 브로드캐스트`() {
        val partyId = 1L
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        wheneverTransitionSucceeds(partyId, PartyPhase.CANDLE, PartyPhase.BURST)

        val result = useCase(partyId, userId = 99L, participantToken = null, currentPhase = PartyPhase.CANDLE)

        assertEquals(PartyPhase.BURST, result.phase)
        verify(participantService).validatePartyMember(party, 99L, null)
        verify(phaseTransitionService).advance(partyId, PartyPhase.CANDLE, PartyPhase.BURST, fixedNow, 99L, null)
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

    private fun wheneverTransitionSucceeds(
        partyId: Long,
        currentPhase: PartyPhase,
        nextPhase: PartyPhase,
    ) {
        whenever(
            phaseTransitionService.advance(
                eq(partyId),
                eq(currentPhase),
                eq(nextPhase),
                any(),
                any(),
                anyOrNull(),
            ),
        ).thenReturn(true)
    }
}
