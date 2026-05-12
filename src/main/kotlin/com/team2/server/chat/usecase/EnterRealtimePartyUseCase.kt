package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.hibernate.Hibernate
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
    data class EnterResult(
        val participantToken: String,
        val partyId: Long,
    )

    @Transactional
    fun enter(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): EnterResult {
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        validateInvite(invite.party, invite.expiresAt)

        val character =
            characterRepository.findByIdOrNull(request.characterId)
                ?: throw BusinessException(ErrorCode.CHARACTER_NOT_FOUND)

        if (userId == null && request.participantToken != null) {
            return reenterAsGuest(invite.party, request.participantToken, request.nickname, character)
        }

        val participant = findOrCreateParticipant(invite.party.id, userId, invite.party)
        val participantToken = upsertProfile(participant, request.nickname, character)

        return EnterResult(participantToken = participantToken, partyId = invite.party.id)
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
        validateEnterable(party)
    }

    private fun validateEnterable(party: Party) {
        val realtimeParty = Hibernate.unproxy(party) as RealtimeParty
        val now = LocalDateTime.now()
        val enterableFrom = realtimeParty.startedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
        val enterableTo = realtimeParty.startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
        if (now.isBefore(enterableFrom) || !now.isBefore(enterableTo)) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
    }

    private fun reenterAsGuest(
        party: Party,
        participantToken: String,
        nickname: String,
        character: Character,
    ): EnterResult {
        val profile =
            realtimeParticipantProfileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        if (profile.participant.party.id != party.id) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        profile.nickname = nickname
        profile.character = character
        return EnterResult(participantToken = profile.participantToken, partyId = party.id)
    }

    private fun upsertProfile(
        participant: Participant,
        nickname: String,
        character: Character,
    ): String {
        val profile = realtimeParticipantProfileRepository.findByParticipant(participant)
        if (profile != null) {
            profile.nickname = nickname
            profile.character = character
            return profile.participantToken
        }
        val newProfile =
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = participant, nickname = nickname, character = character),
            )
        return newProfile.participantToken
    }

    private fun findOrCreateParticipant(
        partyId: Long,
        userId: Long?,
        party: Party,
    ): Participant {
        if (userId == null) {
            return participantRepository.save(Participant(party = party))
        }
        return participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: createParticipantForUser(party, userId)
    }

    private fun createParticipantForUser(
        party: Party,
        userId: Long,
    ): Participant {
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        return participantRepository.save(Participant(party = party, user = user))
    }
}
