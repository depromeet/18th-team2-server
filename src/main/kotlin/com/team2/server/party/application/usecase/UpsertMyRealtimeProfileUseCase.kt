package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.api.dto.UpsertParticipantRealtimeProfileRequest
import com.team2.server.party.application.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.application.service.CharacterService
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
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
    private val characterService: CharacterService,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun invoke(
        inviteToken: String,
        userId: Long,
        request: UpsertParticipantRealtimeProfileRequest,
    ): ParticipantRealtimeProfileResult {
        val invite = partyInviteService.findUsableRealtimeInvite(inviteToken, LocalDateTime.now())
        val character = characterService.requireCharacter(request.characterId)
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(invite.party, user)
        val profile =
            profileService.upsert(
                participant = participant,
                nickname = request.nickname.trim(),
                character = character,
                isHostNicknameLocked = participant.isCelebrant,
            )
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile.nickname,
            character = characterService.toResult(character),
        )
    }
}
