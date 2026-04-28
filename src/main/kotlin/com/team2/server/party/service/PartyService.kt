package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val characterRepository: CharacterRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createParty(
        userId: Long,
        request: CreatePartyRequest,
    ): CreatePartyResponse {
        val startedAt = LocalDateTime.of(request.startedDate, request.startTime)
        val party =
            Party(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = startedAt,
            )
        val saved = partyRepository.save(party)
        return CreatePartyResponse(partyId = saved.id)
    }

    @Transactional(readOnly = true)
    fun getPartyInfo(
        inviteToken: String,
        userId: Long?,
    ): PartyInfoResponse {
        val invite = findValidInvite(inviteToken)
        val party = invite.party

        val myParticipant =
            if (userId != null) {
                val user = userRepository.findById(userId).orElse(null)
                user
                    ?.let { participantRepository.findByPartyAndUser(party, it) }
                    ?.let { ParticipantResponse.from(it, it.character?.let(characterImageUrlResolver::resolve)) }
            } else {
                null
            }

        return PartyInfoResponse.from(party, myParticipant)
    }

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
            } catch (_: DataIntegrityViolationException) {
                throw BusinessException(ErrorCode.ALREADY_JOINED)
            }

        return ParticipantResponse.from(participant, character?.let { characterImageUrlResolver.resolve(it) })
    }

    private fun findValidInvite(inviteToken: String): PartyInvite {
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (invite.expiresAt.isBefore(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }
        return invite
    }

    private fun validateJoinable(party: Party) {
        if (party.endedAt?.isBefore(LocalDateTime.now()) == true) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
    }

    private fun resolveUser(
        party: Party,
        userId: Long?,
    ) = userId?.let {
        val user = userRepository.findById(it).orElse(null)
        if (user != null && participantRepository.existsByPartyAndUser(party, user)) {
            throw BusinessException(ErrorCode.ALREADY_JOINED)
        }
        user
    }

    private fun resolveCharacter(
        party: Party,
        characterId: Long?,
    ) = when {
        party.isChattingAllow && characterId == null -> throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        !party.isChattingAllow && characterId != null -> throw BusinessException(ErrorCode.CHARACTER_NOT_ALLOWED)
        characterId != null ->
            characterRepository
                .findById(characterId)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        else -> null
    }
}
