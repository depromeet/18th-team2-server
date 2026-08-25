package com.team2.server.chat.infrastructure.websocket

import com.team2.server.party.application.port.RealtimePartyPresencePort
import org.springframework.stereotype.Component

@Component
class StompRealtimePartyPresenceAdapter(
    private val stompPartyPresenceRegistry: StompPartyPresenceRegistry,
) : RealtimePartyPresencePort {
    override fun findOnlineParticipantTokens(partyId: Long): Set<String> =
        stompPartyPresenceRegistry.findOnlineParticipantTokens(partyId)
}
