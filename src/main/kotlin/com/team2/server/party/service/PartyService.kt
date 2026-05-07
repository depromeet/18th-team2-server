package com.team2.server.party.service

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.CreateRealtimePartyRequest
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createRealtimeParty(
        userId: Long,
        request: CreateRealtimePartyRequest,
    ): CreatePartyResponse {
        val user = findUser(userId)
        val party =
            RealtimeParty(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = LocalDateTime.of(request.startedDate, request.startTime),
            )
        val saved = partyRepository.save(party)
        val participant =
            participantRepository.save(
                Participant(
                    party = saved,
                    user = user,
                    isCelebrant = true,
                ),
            )
        val character =
            characterRepository
                .findById(request.characterId)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = request.celebrantNickname,
                character = character,
            ),
        )
        return CreatePartyResponse(partyId = saved.id)
    }

    @Transactional
    fun createPaperOnlyParty(
        userId: Long,
        request: CreatePaperOnlyPartyRequest,
    ): CreatePartyResponse {
        val user = findUser(userId)
        val party =
            PaperOnlyParty(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = request.startedDate.atStartOfDay(),
            )
        val saved = partyRepository.save(party)
        participantRepository.save(
            Participant(
                party = saved,
                user = user,
                isCelebrant = true,
            ),
        )
        return CreatePartyResponse(partyId = saved.id)
    }

    @Transactional
    fun deleteParty(
        partyId: Long,
        userId: Long,
    ) {
        val party = findParty(partyId)

        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

        if (!LocalDateTime.now().isBefore(party.startedAt)) {
            throw BusinessException(ErrorCode.PARTY_ALREADY_STARTED)
        }

        val participants = participantRepository.findAllByPartyId(partyId)

        chatMessageRepository.deleteAllByPartyId(partyId)
        rollingPaperRepository.deleteAllByPartyId(partyId)
        if (party is RealtimeParty) {
            val participantIds = participants.map { it.id }
            realtimeParticipantProfileRepository.deleteAllByParticipantIdIn(participantIds)
        }
        participantRepository.deleteAll(participants)
        partyInviteRepository.deleteAllByPartyId(partyId)
        partyRepository.delete(party)
    }

    private fun findParty(partyId: Long) =
        partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    private fun findUser(userId: Long) =
        userRepository
            .findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
}
