package com.team2.server.party.service

import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val characterRepository: CharacterRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
    private val userRepository: UserRepository,
) {
    fun getPartyInfo(
        shareLink: String,
        userId: Long?,
    ): PartyInfoResponse {
        val party =
            partyRepository.findByShareLink(shareLink)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

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
        shareLink: String,
        userId: Long?,
        nickname: String,
        characterId: Long?,
    ): ParticipantResponse {
        val party =
            partyRepository.findByShareLink(shareLink)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (party.endedAt?.isBefore(LocalDateTime.now()) == true) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }

        val user = resolveUser(party, userId)

        val character =
            when {
                party.isChattingAllow && characterId == null -> throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
                !party.isChattingAllow && characterId != null -> throw BusinessException(ErrorCode.CHARACTER_NOT_ALLOWED)
                characterId != null ->
                    characterRepository
                        .findById(characterId)
                        .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
                else -> null
            }

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

    private fun resolveUser(
        party: Party,
        userId: Long?,
    ) = userId?.let {
        val u = userRepository.findById(it).orElse(null)
        if (u != null && participantRepository.existsByPartyAndUser(party, u)) {
            throw BusinessException(ErrorCode.ALREADY_JOINED)
        }
        u
    }
}
