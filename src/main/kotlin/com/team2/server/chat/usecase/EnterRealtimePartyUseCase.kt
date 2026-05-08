package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EnterRealtimePartyUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun enter(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): EnterRealtimePartyResponse {
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        val party = invite.party
        validateInvite(party, invite.expiresAt)

        val character =
            characterRepository.findByIdOrNull(request.characterId)
                ?: throw BusinessException(ErrorCode.CHARACTER_NOT_FOUND)

        val participant = findOrCreateParticipant(party.id, userId, party)
        val profile = realtimeParticipantProfileRepository.findByParticipant(participant)

        if (profile != null) {
            profile.nickname = request.nickname
            profile.character = character
            return EnterRealtimePartyResponse(participantToken = profile.participantToken)
        }

        val newProfile =
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(
                    participant = participant,
                    nickname = request.nickname,
                    character = character,
                ),
            )
        return EnterRealtimePartyResponse(participantToken = newProfile.participantToken)
    }

    private fun validateInvite(
        party: Party,
        expiresAt: LocalDateTime,
    ) {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
    }

    private fun findOrCreateParticipant(
        partyId: Long,
        userId: Long?,
        party: Party,
    ): Participant {
        if (userId != null) {
            return participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: run {
                    val user =
                        userRepository.findByIdOrNull(userId)
                            ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
                    participantRepository.save(Participant(party = party, user = user))
                }
        }
        return participantRepository.save(Participant(party = party))
    }
}
