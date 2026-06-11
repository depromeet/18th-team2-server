package com.team2.server.chat.infrastructure.sse

import com.team2.server.party.application.port.RealtimePartyPresencePort
import org.springframework.stereotype.Component

@Component
class SseRealtimePartyPresenceAdapter(
    private val sseEmitterRegistry: SseEmitterRegistry,
) : RealtimePartyPresencePort {
    override fun findOnlineParticipantTokens(partyId: Long): Set<String> =
        sseEmitterRegistry.findParticipantTokens(partyId)
}
