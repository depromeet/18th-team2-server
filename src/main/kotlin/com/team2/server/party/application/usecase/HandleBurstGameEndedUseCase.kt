package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.RealtimePartyEndService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class HandleBurstGameEndedUseCase(
    private val realtimePartyEndService: RealtimePartyEndService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(partyId: Long): Boolean {
        val now = LocalDateTime.now(clock)
        return realtimePartyEndService.canNotifyHostEndAvailable(partyId, now)
    }
}
