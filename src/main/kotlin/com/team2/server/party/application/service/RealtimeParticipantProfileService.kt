package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service

@Service
class RealtimeParticipantProfileService(
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
) {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile? =
        profileRepository.findByParticipant(participant)

    fun upsert(
        participant: Participant,
        nickname: String,
        character: Character,
        isHostNicknameLocked: Boolean,
    ): RealtimeParticipantProfile {
        val existing = profileRepository.findByParticipant(participant)
        if (existing == null) {
            validateNicknameUnique(participant, nickname)
            return profileRepository.save(
                RealtimeParticipantProfile(
                    participant = participant,
                    nickname = nickname,
                    character = character,
                ),
            )
        }
        if (existing.nickname != nickname) {
            if (isHostNicknameLocked) {
                throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
            }
            validateNicknameUnique(participant, nickname)
        }
        existing.nickname = nickname
        existing.character = character
        return existing
    }

    private fun validateNicknameUnique(
        participant: Participant,
        nickname: String,
    ) {
        val duplicated =
            profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                partyId = participant.party.id,
                nickname = nickname,
                excludingParticipantId = participant.id,
            )
        if (duplicated) {
            throw BusinessException(ErrorCode.PARTY_NICKNAME_DUPLICATED)
        }
    }

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
