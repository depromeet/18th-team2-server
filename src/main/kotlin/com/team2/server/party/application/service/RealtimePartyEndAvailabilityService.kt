package com.team2.server.party.application.service

import com.team2.server.burstgame.application.event.BurstGameEndedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RealtimePartyEndAvailabilityService {
    private val burstGameEndedPartyIds = ConcurrentHashMap.newKeySet<Long>()

    fun canEndByBurstGame(partyId: Long): Boolean = burstGameEndedPartyIds.contains(partyId)

    @EventListener
    fun onBurstGameEnded(event: BurstGameEndedEvent) {
        burstGameEndedPartyIds.add(event.partyId)
    }
}
