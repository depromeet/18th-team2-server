package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PartyParticipationService(
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val characterRepository: CharacterRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun joinParty(
        inviteToken: String,
        userId: Long?,
        nickname: String,
        characterId: Long?,
    ): ParticipantResponse {
        val invite = findValidInvite(inviteToken)
        val party = invite.party

        validateJoinable(party)
        val user = resolveUser(party, userId)
        val character = resolveCharacter(party, characterId)

        val participant =
            try {
                participantRepository.saveAndFlush(
                    Participant(
                        party = party,
                        character = character,
                        user = user,
                        nickname = nickname,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                if (e.isConstraintViolation(PARTICIPANT_UNIQUE_CONSTRAINT)) {
                    throw BusinessException(ErrorCode.ALREADY_JOINED)
                }
                throw e
            }

        return ParticipantResponse.from(participant, character?.let { characterImageUrlResolver.resolve(it) })
    }

    private fun findValidInvite(inviteToken: String): PartyInvite {
        val now = LocalDateTime.now()
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (!invite.expiresAt.isAfter(now)) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
        return invite
    }

    private fun validateJoinable(party: Party) {
        val now = LocalDateTime.now()
        if (party.endedAt?.let { !it.isAfter(now) } == true) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
    }

    private fun resolveUser(
        party: Party,
        userId: Long?,
    ) = userId?.let {
        val user = findUser(it)
        if (participantRepository.existsByPartyAndUser(party, user)) {
            throw BusinessException(ErrorCode.ALREADY_JOINED)
        }
        user
    }

    private fun findUser(userId: Long) =
        userRepository
            .findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }

    private fun resolveCharacter(
        party: Party,
        characterId: Long?,
    ): Character? {
        validateCharacterSelection(party, characterId)
        return characterId?.let {
            characterRepository
                .findById(it)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        }
    }

    private fun validateCharacterSelection(
        party: Party,
        characterId: Long?,
    ) {
        when {
            party.isChattingAllow && characterId == null -> throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
            !party.isChattingAllow && characterId != null -> throw BusinessException(ErrorCode.CHARACTER_NOT_ALLOWED)
        }
    }

    private fun DataIntegrityViolationException.isConstraintViolation(constraintName: String): Boolean {
        val message =
            listOfNotNull(
                message,
                rootCause?.message,
                mostSpecificCause.message,
            ).joinToString(" ")
        return message.contains(constraintName, ignoreCase = true)
    }

    companion object {
        private const val PARTICIPANT_UNIQUE_CONSTRAINT = "uk_participant_party_user"
    }
}
