package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service

@Service
class RealtimeParticipantProfileService(
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
            return profileRepository.save(
                RealtimeParticipantProfile(
                    participant = participant,
                    nickname = nickname,
                    character = character,
                ),
            )
        }
        if (isHostNicknameLocked && existing.nickname != nickname) {
            throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
        }
        existing.nickname = nickname
        existing.character = character
        return existing
    }
}
