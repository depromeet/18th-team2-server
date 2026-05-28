package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyHostEnteredScheduleData
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class FindRealtimePartiesWithHostEnteredUseCase(
    private val partyService: PartyService,
) {
    @Transactional(readOnly = true)
    operator fun invoke(hostEnteredAfter: LocalDateTime): List<RealtimePartyHostEnteredScheduleData> =
        partyService
            .findRealtimePartiesWithHostEnteredAfter(hostEnteredAfter)
            .map { RealtimePartyHostEnteredScheduleData.from(it) }
}
