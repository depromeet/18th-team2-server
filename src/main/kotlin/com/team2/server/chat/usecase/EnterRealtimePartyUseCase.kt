package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.hibernate.Hibernate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class EnterRealtimePartyUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    data class EnterResult(
        val participantToken: String,
        val partyId: Long,
        val startedAt: LocalDateTime,
        val isCelebrant: Boolean,
        val nickname: String,
        val characterId: Long?,
        val partyState: RealtimePartyStateResult,
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
        val realtimeParty = validateInvite(invite.party, invite.expiresAt)
        val reentry = request.participantToken != null
        if (!reentry) {
            validateNewEnterable(realtimeParty)
        }

        val character =
            characterRepository.findByIdOrNull(request.characterId)
                ?: throw BusinessException(ErrorCode.CHARACTER_NOT_FOUND)

        val profile =
            if (reentry) {
                reenterByParticipantToken(
                    party = realtimeParty,
                    participantToken = requireNotNull(request.participantToken),
                    nickname = request.nickname,
                    character = character,
                )
            } else {
                val participant = findOrCreateParticipant(invite.party.id, userId, invite.party)
                upsertProfile(participant, request.nickname, character)
            }

        return EnterResult(
            participantToken = profile.participantToken,
            partyId = invite.party.id,
            startedAt = invite.party.startedAt,
            isCelebrant = profile.participant.isCelebrant,
            nickname = profile.nickname,
            characterId = character.id,
            partyState = RealtimePartyStateResult.from(realtimeParty, LocalDateTime.now(clock)),
        )
    }

    private fun validateInvite(
        party: Party,
        expiresAt: LocalDateTime,
    ): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        if (!expiresAt.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }

    private fun validateNewEnterable(realtimeParty: RealtimeParty) {
        if (realtimeParty.status(LocalDateTime.now(clock)) != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
    }

    private fun reenterByParticipantToken(
        party: RealtimeParty,
        participantToken: String,
        nickname: String,
        character: Character,
    ): RealtimeParticipantProfile {
        val profile =
            realtimeParticipantProfileRepository.findByParticipantToken(participantToken)
                ?: throwUnauthorized()
        if (profile.participant.party.id != party.id) {
            throwForbidden()
        }
        val reconnectableStatuses =
            listOf(
                RealtimePartyStatus.LIVE_OPEN,
                RealtimePartyStatus.LIVE_ENDING,
            )
        if (party.status(LocalDateTime.now(clock)) !in reconnectableStatuses) {
            throwChatNotActive()
        }
        profile.nickname = nickname
        profile.character = character
        return profile
    }

    private fun throwUnauthorized(): Nothing = throw BusinessException(ErrorCode.UNAUTHORIZED)

    private fun throwForbidden(): Nothing = throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

    private fun throwChatNotActive(): Nothing = throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)

    private fun upsertProfile(
        participant: Participant,
        nickname: String,
        character: Character,
    ): RealtimeParticipantProfile {
        val profile = realtimeParticipantProfileRepository.findByParticipant(participant)
        if (profile != null) {
            profile.nickname = nickname
            profile.character = character
            return profile
        }
        return realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(participant = participant, nickname = nickname, character = character),
        )
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
