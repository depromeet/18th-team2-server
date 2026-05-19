package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
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
class GetMyRealtimeProfileUseCase(
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
    ): ParticipantRealtimeProfileResult {
        val invite = partyInviteService.findUsableRealtimeInvite(inviteToken, LocalDateTime.now())
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(invite.party, user)
        val profile = profileService.findByParticipant(participant)
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile?.nickname,
            character = profile?.character?.let { characterService.toResult(it) },
        )
    }
}
