package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyScheduleData
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class FindRealtimePartiesWaitingAutomaticEndingUseCase(
    private val partyService: PartyService,
) {
    @Transactional(readOnly = true)
    operator fun invoke(startedAfter: LocalDateTime): List<RealtimePartyScheduleData> =
        partyService
            .findRealtimePartiesWaitingAutomaticEnding(startedAfter)
            .map { RealtimePartyScheduleData.from(it) }
}
