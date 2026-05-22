package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.service.ParticipantService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyStateUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val participantService: ParticipantService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimePartyStateResult {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        participantService.validatePartyMember(party, userId, participantToken)
        return RealtimePartyStateResult.from(party, LocalDateTime.now(clock))
    }
}
