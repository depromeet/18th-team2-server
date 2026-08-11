package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class AdvancePartyPhaseActorValidator(
    private val participantService: ParticipantService,
) {
    fun validate(
        party: RealtimeParty,
        currentPhase: PartyPhase,
        now: LocalDateTime,
        userId: Long?,
        participantToken: String?,
    ) {
        when (currentPhase) {
            PartyPhase.ENTRY -> validateEntry(party, now, userId)
            PartyPhase.MUSIC,
            PartyPhase.CANDLE,
            -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }
    }

    private fun validateEntry(
        party: RealtimeParty,
        now: LocalDateTime,
        userId: Long?,
    ) {
        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        if (now.isBefore(party.startedAt)) throw BusinessException(ErrorCode.REALTIME_PARTY_INVALID_STATE)
    }
}
