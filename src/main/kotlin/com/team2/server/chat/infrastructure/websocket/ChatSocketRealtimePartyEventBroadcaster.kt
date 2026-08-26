package com.team2.server.chat.infrastructure.websocket

import com.team2.server.chat.dto.PartyEndedEventPayload
import com.team2.server.chat.dto.PartyEndingEventPayload
import com.team2.server.chat.dto.PartyPhaseChangedEventPayload
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * WebSocket 채널로 파티 진행 이벤트(종료 카운트다운/종료/단계 전환)를 브로드캐스트한다.
 *
 * [com.team2.server.chat.infrastructure.sse.ChatRealtimePartyEventBroadcaster]와 함께
 * 같은 포트의 서로 다른 구현체로 등록된다. 호출부([com.team2.server.party.application.service.PartyPhaseTransitionService],
 * [com.team2.server.party.infrastructure.sse.PartyEndScheduler])는 `List<RealtimePartyEventBroadcaster>`로
 * 주입받아 두 구현체 모두에 자동으로 팬아웃한다 — SSE/WebSocket 양쪽 클라이언트가 동일한 이벤트를 받는다.
 */
@Component
class ChatSocketRealtimePartyEventBroadcaster(
    private val chatSocketGateway: ChatSocketGateway,
) : RealtimePartyEventBroadcaster {
    override fun broadcastPartyEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
        hostNickname: String,
        serverNow: LocalDateTime,
    ) {
        chatSocketGateway.broadcastAfterCommit(
            partyId,
            "party-ending",
            PartyEndingEventPayload(
                partyId = partyId,
                endingStartedAt = endingStartedAt,
                endedAt = endedAt,
                endingReason = endingReason,
                hostNickname = hostNickname,
                serverNow = serverNow,
            ),
        )
    }

    override fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
        hostNickname: String,
        serverNow: LocalDateTime,
    ) {
        chatSocketGateway.broadcastAfterCommit(
            partyId,
            "party-ended",
            PartyEndedEventPayload(
                partyId = partyId,
                endedAt = endedAt,
                hostNickname = hostNickname,
                serverNow = serverNow,
            ),
        )
    }

    /**
     * WebSocket에는 SSE의 emitter 레지스트리 같은 파티 단위 세션 목록이 없다 — STOMP 구독은 브로커가
     * 관리하고, 세션 종료는 클라이언트가 party-ended 수신 후 스스로 접속을 끊는 것을 전제로 한다.
     * 정리할 자원이 없으므로 아무 것도 하지 않는다.
     */
    override fun completeParty(partyId: Long) = Unit

    override fun broadcastPhaseChanged(
        partyId: Long,
        phase: PartyPhase,
        phaseStartedAt: LocalDateTime,
        serverNow: LocalDateTime,
    ) {
        chatSocketGateway.broadcastAfterCommit(
            partyId,
            "party-phase-changed",
            PartyPhaseChangedEventPayload(
                partyId = partyId,
                phase = phase,
                phaseStartedAt = phaseStartedAt,
                serverNow = serverNow,
            ),
        )
    }
}
