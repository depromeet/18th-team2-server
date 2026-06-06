package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyStateUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val endingInfoPort: RealtimePartyEndingInfoPort,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimePartyStateResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        participantService.validatePartyMember(party, userId, participantToken)
        return RealtimePartyStateResult.from(party, now, endingInfoPort.get(party, now))
    }
}
