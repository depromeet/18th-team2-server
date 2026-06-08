package com.team2.server.party.application.service

import com.team2.server.party.application.port.BurstGameStartPort
import com.team2.server.party.application.port.CandleBlowStartPort
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyPhaseTransitionServiceTest {
    private val phaseStore: PartyPhaseStore = mock()
    private val eventBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val burstGameStartPort: BurstGameStartPort = mock()
    private val candleBlowStartPort: CandleBlowStartPort = mock()
    private val service =
        PartyPhaseTransitionService(
            phaseStore = phaseStore,
            eventBroadcaster = eventBroadcaster,
            burstGameStartPort = burstGameStartPort,
            candleBlowStartPort = candleBlowStartPort,
        )
    private val now = LocalDateTime.of(2026, 6, 8, 20, 30)

    @Test
    fun `MUSIC에서 CANDLE로 전환되면 phase 이벤트와 촛불끄기 시작을 처리한다`() {
        whenever(phaseStore.advance(1L, PartyPhase.MUSIC, PartyPhase.CANDLE, now)).thenReturn(true)

        val advanced =
            service.advance(
                partyId = 1L,
                currentPhase = PartyPhase.MUSIC,
                nextPhase = PartyPhase.CANDLE,
                now = now,
                userId = 10L,
                participantToken = null,
            )

        assertTrue(advanced)
        verify(eventBroadcaster).broadcastPhaseChanged(1L, PartyPhase.CANDLE, now, now)
        verify(candleBlowStartPort).start(1L, now)
        verify(burstGameStartPort, never()).start(any(), any(), any())
    }

    @Test
    fun `CANDLE에서 BURST로 전환하기 전에 burst 게임을 시작한다`() {
        whenever(phaseStore.advance(eq(1L), eq(PartyPhase.CANDLE), eq(PartyPhase.BURST), eq(now), any()))
            .thenAnswer { invocation ->
                invocation.getArgument<() -> Unit>(4).invoke()
                true
            }

        val advanced =
            service.advance(
                partyId = 1L,
                currentPhase = PartyPhase.CANDLE,
                nextPhase = PartyPhase.BURST,
                now = now,
                userId = 10L,
                participantToken = "participant-token",
            )

        assertTrue(advanced)
        verify(burstGameStartPort).start(1L, 10L, "participant-token")
        verify(eventBroadcaster).broadcastPhaseChanged(1L, PartyPhase.BURST, now, now)
        verify(candleBlowStartPort, never()).start(any(), any())
    }

    @Test
    fun `CAS 실패 시 phase 이벤트와 CANDLE 후속 처리를 하지 않는다`() {
        whenever(phaseStore.advance(eq(1L), eq(PartyPhase.MUSIC), eq(PartyPhase.CANDLE), any())).thenReturn(false)

        val advanced =
            service.advance(
                partyId = 1L,
                currentPhase = PartyPhase.MUSIC,
                nextPhase = PartyPhase.CANDLE,
                now = now,
                userId = 10L,
                participantToken = null,
            )

        assertFalse(advanced)
        verify(eventBroadcaster, never()).broadcastPhaseChanged(any(), any(), any(), any())
        verify(candleBlowStartPort, never()).start(any(), any())
    }
}
