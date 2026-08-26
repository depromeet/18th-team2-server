package com.team2.server.chat.infrastructure.sse

import com.team2.server.chat.dto.PartyEndedEventPayload
import com.team2.server.chat.dto.PartyEndingEventPayload
import com.team2.server.chat.dto.PartyPhaseChangedEventPayload
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime

@Component
class ChatRealtimePartyEventBroadcaster(
    private val sseEmitterRegistry: SseEmitterRegistry,
) : RealtimePartyEventBroadcaster {
    override fun broadcastPartyEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
        hostNickname: String,
        serverNow: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-ending")
                .data(
                    PartyEndingEventPayload(
                        partyId = partyId,
                        endingStartedAt = endingStartedAt,
                        endedAt = endedAt,
                        endingReason = endingReason,
                        hostNickname = hostNickname,
                        serverNow = serverNow,
                    ),
                ).build(),
        )
    }

    override fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
        hostNickname: String,
        serverNow: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-ended")
                .data(
                    PartyEndedEventPayload(
                        partyId = partyId,
                        endedAt = endedAt,
                        hostNickname = hostNickname,
                        serverNow = serverNow,
                    ),
                ).build(),
        )
    }

    override fun completeParty(partyId: Long) {
        sseEmitterRegistry.completeAll(partyId)
    }

    override fun broadcastPhaseChanged(
        partyId: Long,
        phase: PartyPhase,
        phaseStartedAt: LocalDateTime,
        serverNow: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-phase-changed")
                .data(PartyPhaseChangedEventPayload(partyId, phase, phaseStartedAt, serverNow))
                .build(),
        )
    }
}
