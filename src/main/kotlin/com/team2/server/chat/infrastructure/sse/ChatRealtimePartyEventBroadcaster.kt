package com.team2.server.chat.infrastructure.sse

import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime

@Component
class ChatRealtimePartyEventBroadcaster(
    private val sseEmitterRegistry: SseEmitterRegistry,
) : RealtimePartyEventBroadcaster {
    override fun broadcastHostEndAvailable(
        partyId: Long,
        availableAt: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcastHost(
            partyId,
            SseEmitter
                .event()
                .name("host-end-available")
                .data(HostEndAvailablePayload(partyId = partyId, availableAt = availableAt))
                .build(),
        )
    }

    override fun broadcastPartyEnding(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
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
                    ),
                ).build(),
        )
    }

    override fun broadcastPartyEnded(
        partyId: Long,
        endedAt: LocalDateTime,
    ) {
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter
                .event()
                .name("party-ended")
                .data(PartyEndedPayload(partyId = partyId, endedAt = endedAt))
                .build(),
        )
    }

    override fun completeParty(partyId: Long) {
        sseEmitterRegistry.completeAll(partyId)
    }

    data class HostEndAvailablePayload(
        val partyId: Long,
        val availableAt: LocalDateTime,
    )

    data class PartyEndingPayload(
        val partyId: Long,
        val endingStartedAt: LocalDateTime,
        val endedAt: LocalDateTime,
    )

    data class PartyEndedPayload(
        val partyId: Long,
        val endedAt: LocalDateTime,
    )
}
