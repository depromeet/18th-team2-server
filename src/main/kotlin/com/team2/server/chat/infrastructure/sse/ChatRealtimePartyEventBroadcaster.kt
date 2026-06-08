package com.team2.server.chat.infrastructure.sse

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
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-ending")
                .data(
                    PartyEndingPayload(
                        partyId = partyId,
                        endingStartedAt = endingStartedAt,
                        endedAt = endedAt,
                        endingReason = endingReason,
                        hostNickname = hostNickname,
                    ),
                ).build(),
        )
    }

    override fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
        hostNickname: String,
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-ended")
                .data(PartyEndedPayload(partyId = partyId, endedAt = endedAt, hostNickname = hostNickname))
                .build(),
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
                .data(PhaseChangedPayload(partyId, phase, phaseStartedAt, serverNow))
                .build(),
        )
    }

    data class PartyEndingPayload(
        val partyId: Long,
        val endingStartedAt: LocalDateTime,
        val endedAt: LocalDateTime,
        val endingReason: RealtimePartyEndingReason,
        val hostNickname: String,
    )

    data class PartyEndedPayload(
        val partyId: Long,
        val endedAt: LocalDateTime,
        val hostNickname: String,
    )

    data class PhaseChangedPayload(
        val partyId: Long,
        val phase: PartyPhase,
        val phaseStartedAt: LocalDateTime,
        val serverNow: LocalDateTime,
    )
}
