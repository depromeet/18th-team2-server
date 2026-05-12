package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.PartyInviteParticipationResponse
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class JoinPartyInviteUseCase(
    private val userRepository: UserRepository,
    private val participantService: ParticipantService,
    private val partyInviteService: PartyInviteService,
) {
    @Transactional
    fun join(
        inviteToken: String,
        userId: Long,
    ): PartyInviteParticipationResponse {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)

        val party = invite.party
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }

        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(party, user)
        return PartyInviteParticipationResponse(participantId = participant.id)
    }
}
