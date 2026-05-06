package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.PartyInviteParticipationResponse
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.service.ParticipantService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class JoinPartyInviteUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val userRepository: UserRepository,
    private val participantService: ParticipantService,
) {
    @Transactional
    fun join(
        inviteToken: String,
        userId: Long,
    ): PartyInviteParticipationResponse {
        val invite = findInvite(inviteToken)
        val now = LocalDateTime.now()
        validateInvite(invite, now)

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

    private fun findInvite(inviteToken: String): PartyInvite =
        partyInviteRepository.findByToken(inviteToken)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    private fun validateInvite(
        invite: PartyInvite,
        now: LocalDateTime,
    ) {
        if (!invite.expiresAt.isAfter(now)) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
    }
}
