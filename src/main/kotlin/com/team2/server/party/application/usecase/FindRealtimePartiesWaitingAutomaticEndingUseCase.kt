package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyScheduleData
import com.team2.server.party.application.service.PartyQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class FindRealtimePartiesWaitingAutomaticEndingUseCase(
    private val partyQueryService: PartyQueryService,
) {
    @Transactional(readOnly = true)
    operator fun invoke(startedAfter: LocalDateTime): List<RealtimePartyScheduleData> =
        partyQueryService
            .findRealtimePartiesWaitingAutomaticEnding(startedAfter)
            .map { RealtimePartyScheduleData.from(it) }
}
