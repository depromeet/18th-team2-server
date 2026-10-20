package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyDeletedReason
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class DeletePartyUseCase(
    private val partyService: PartyService,
    private val eventBroadcasters: List<RealtimePartyEventBroadcaster>,
    private val clock: Clock,
) {
    @Transactional
    fun delete(
        partyId: Long,
        userId: Long,
    ) {
        val party = partyService.deleteParty(partyId = partyId, userId = userId)
        if (party is RealtimeParty) {
            val now = LocalDateTime.now(clock)
            eventBroadcasters.forEach { it.broadcastPartyDeleted(partyId, PartyDeletedReason.HOST_DELETED, now) }
        }
    }
}
