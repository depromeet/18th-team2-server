package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterResult
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.dto.UpsertParticipantRealtimeProfileRequest
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.service.ParticipantService
import com.team2.server.party.service.PartyInviteService
import com.team2.server.party.service.RealtimeParticipantProfileService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UpsertMyRealtimeProfileUseCase(
    private val partyInviteService: PartyInviteService,
    private val participantService: ParticipantService,
    private val profileService: RealtimeParticipantProfileService,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
) {
    @Transactional
    fun invoke(
        inviteToken: String,
        userId: Long,
        request: UpsertParticipantRealtimeProfileRequest,
    ): ParticipantRealtimeProfileResult {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = invite.party
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        val characterId = request.characterId
            ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        val character =
            characterRepository.findById(characterId).orElseThrow {
                BusinessException(ErrorCode.CHARACTER_NOT_FOUND)
            }
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(party, user)
        val nickname = request.nickname.trim()
        if (nickname.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val profile =
            profileService.upsert(
                participant = participant,
                nickname = nickname,
                character = character,
                isHostNicknameLocked = participant.isCelebrant,
            )
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile.nickname,
            character =
                CharacterResult(
                    characterId = profile.character?.id ?: character.id,
                    name = profile.character?.name ?: character.name,
                    characterImageUrl = characterImageUrlResolver.resolve(profile.character ?: character),
                    characterThumbnailImageUrl = null,
                ),
        )
    }
}
