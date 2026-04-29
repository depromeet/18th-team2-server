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
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createParty(
        userId: Long,
        request: CreatePartyRequest,
        partyOption: PartyOption,
    ): CreatePartyResponse {
        val user = findUser(userId)
        val startedAt = LocalDateTime.of(request.startedDate, request.startTime ?: LocalTime.MIDNIGHT)
        val party =
            Party(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = startedAt,
                option = partyOption,
            )
        val saved = partyRepository.save(party)
        participantRepository.save(
            Participant(
                party = saved,
                user = user,
                nickname = request.celebrantNickname,
                isCelebrant = true,
            ),
        )
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
                val user = findUser(userId)
                participantRepository
                    .findByPartyAndUser(party, user)
                    ?.let { ParticipantResponse.from(it, it.character?.let(characterImageUrlResolver::resolve)) }
            } else {
                null
            }

        return PartyInfoResponse.from(party, myParticipant)
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

    private fun findUser(userId: Long) =
        userRepository
            .findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
}
