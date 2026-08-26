package com.team2.server.chat.infrastructure.websocket

import com.team2.server.chat.dto.PartyEndedEventPayload
import com.team2.server.chat.dto.PartyEndingEventPayload
import com.team2.server.chat.dto.PartyPhaseChangedEventPayload
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDateTime

class ChatSocketRealtimePartyEventBroadcasterTest {
    private val chatSocketGateway: ChatSocketGateway = mock()
    private val broadcaster = ChatSocketRealtimePartyEventBroadcaster(chatSocketGateway)
    private val now = LocalDateTime.of(2026, 5, 23, 14, 30)

    @Test
    fun `party-ending을 WebSocket 브로드캐스트 토픽으로 전송한다`() {
        broadcaster.broadcastPartyEnding(
            partyId = 1L,
            endingStartedAt = now,
            endedAt = now.plusSeconds(60),
            endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
            hostNickname = "주최자",
            serverNow = now.plusSeconds(1),
        )

        verify(chatSocketGateway).broadcastAfterCommit(
            1L,
            "party-ending",
            PartyEndingEventPayload(
                partyId = 1L,
                endingStartedAt = now,
                endedAt = now.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                serverNow = now.plusSeconds(1),
            ),
        )
    }

    @Test
    fun `party-ended를 WebSocket 브로드캐스트 토픽으로 전송한다`() {
        broadcaster.broadcastPartyEnded(
            partyId = 1L,
            endedAt = now.plusSeconds(60),
            hostNickname = "주최자",
            serverNow = now.plusSeconds(60),
        )

        verify(chatSocketGateway).broadcastAfterCommit(
            1L,
            "party-ended",
            PartyEndedEventPayload(
                partyId = 1L,
                endedAt = now.plusSeconds(60),
                hostNickname = "주최자",
                serverNow = now.plusSeconds(60),
            ),
        )
    }

    @Test
    fun `party-phase-changed를 WebSocket 브로드캐스트 토픽으로 전송한다`() {
        broadcaster.broadcastPhaseChanged(
            partyId = 1L,
            phase = PartyPhase.CANDLE,
            phaseStartedAt = now,
            serverNow = now,
        )

        verify(chatSocketGateway).broadcastAfterCommit(
            1L,
            "party-phase-changed",
            PartyPhaseChangedEventPayload(
                partyId = 1L,
                phase = PartyPhase.CANDLE,
                phaseStartedAt = now,
                serverNow = now,
            ),
        )
    }

    @Test
    fun `completeParty는 정리할 세션 자원이 없어 아무 것도 전송하지 않는다`() {
        broadcaster.completeParty(partyId = 1L)

        verify(chatSocketGateway, never()).broadcastAfterCommit(any(), any(), any())
    }
}
