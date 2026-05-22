package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service

@Service
class PartyCallerAccessService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
) {
    fun validateCallerCanAccessParty(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        if (participantToken != null && participantTokenMatchesParty(partyId, participantToken)) {
            return
        }
        if (userId == null) {
            throw BusinessException(if (participantToken == null) ErrorCode.UNAUTHORIZED else ErrorCode.PARTY_FORBIDDEN)
        }
        if (
            partyRepository.existsByIdAndOwnerId(partyId, userId) ||
            participantRepository.existsByPartyIdAndUserId(partyId, userId)
        ) {
            return
        }
        throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
    }

    private fun participantTokenMatchesParty(
        partyId: Long,
        participantToken: String,
    ): Boolean {
        val profile = realtimeParticipantProfileRepository.findByParticipantToken(participantToken) ?: return false
        return profile.participant.party.id == partyId
    }
}
