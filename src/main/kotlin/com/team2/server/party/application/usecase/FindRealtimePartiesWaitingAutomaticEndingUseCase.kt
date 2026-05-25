package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class FindRealtimePartiesWaitingAutomaticEndingUseCase(
    private val partyService: PartyService,
) {
    @Transactional(readOnly = true)
    operator fun invoke(startedAfter: LocalDateTime): List<RealtimeParty> =
        partyService.findRealtimePartiesWaitingAutomaticEnding(startedAfter)
}
