package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service

@Service
class RealtimeParticipantProfileService(
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
) {
    fun resolveProfile(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimeParticipantProfile {
        if (userId != null) return resolveByUserId(partyId, userId)
        if (participantToken != null) return resolveByToken(participantToken, partyId)
        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }

    private fun resolveByUserId(
        partyId: Long,
        userId: Long,
    ): RealtimeParticipantProfile {
        val participant =
            participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        return profileRepository.findByParticipant(participant)
            ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
    }

    private fun resolveByToken(
        participantToken: String,
        partyId: Long,
    ): RealtimeParticipantProfile {
        val profile =
            profileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        if (profile.participant.party.id != partyId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        return profile
    }
}
