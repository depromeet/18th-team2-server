package com.team2.server.party.application.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RealtimePartyEndAvailabilityService {
    private val burstGameEndedPartyIds = ConcurrentHashMap.newKeySet<Long>()

    fun canEndByBurstGame(partyId: Long): Boolean = burstGameEndedPartyIds.contains(partyId)

    fun markBurstGameEnded(partyId: Long) {
        burstGameEndedPartyIds.add(partyId)
    }
}
