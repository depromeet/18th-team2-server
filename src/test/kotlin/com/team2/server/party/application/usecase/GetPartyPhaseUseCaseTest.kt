package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class GetPartyPhaseUseCaseTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val phaseStore: PartyPhaseStore = mock()
    private val fixedNow = LocalDateTime.of(2026, 5, 26, 20, 0, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = GetPartyPhaseUseCase(partyService, participantService, phaseStore, clock)

    @Test
    fun `phase 등록 전이면 ENTRY와 파티 startedAt 반환`() {
        val partyId = 1L
        val partyStartedAt = LocalDateTime.of(2026, 5, 26, 19, 55)
        val party = RealtimeParty(ownerId = 10L, startedAt = partyStartedAt)
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.getEntry(partyId)).thenReturn(null)

        val result = useCase(partyId, userId = 10L, participantToken = null)

        assertEquals(PartyPhase.ENTRY, result.phase)
        assertEquals(partyStartedAt, result.phaseStartedAt)
        assertEquals(fixedNow, result.serverNow)
    }

    @Test
    fun `phase 등록 후 해당 phase와 startedAt 반환`() {
        val partyId = 1L
        val phaseStartedAt = LocalDateTime.of(2026, 5, 26, 20, 0, 5)
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.of(2026, 5, 26, 19, 55))
        whenever(partyService.requireRealtimeParty(partyId)).thenReturn(party)
        whenever(phaseStore.getEntry(partyId)).thenReturn(
            PartyPhaseStore.PhaseEntry(PartyPhase.MUSIC, phaseStartedAt),
        )

        val result = useCase(partyId, userId = 10L, participantToken = null)

        assertEquals(partyId, result.partyId)
        assertEquals(PartyPhase.MUSIC, result.phase)
        assertEquals(phaseStartedAt, result.phaseStartedAt)
        assertEquals(fixedNow, result.serverNow)
    }
}
