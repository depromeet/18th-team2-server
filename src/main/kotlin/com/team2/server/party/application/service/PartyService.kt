package com.team2.server.party.application.service

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.user.entity.User
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
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
) {
    fun createRealtimeParty(
        userId: Long,
        user: User,
        command: CreateRealtimePartyCommand,
    ): Long {
        requireOwnerUserMatches(userId, user)
        val party =
            RealtimeParty(
                ownerId = userId,
                celebrantNickname = command.celebrantNickname,
                startedAt = LocalDateTime.of(command.startedDate, command.startTime),
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
                .findById(command.characterId)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = command.celebrantNickname,
                character = character,
            ),
        )
        return saved.id
    }

    fun createPaperOnlyParty(
        userId: Long,
        user: User,
        command: CreatePaperOnlyPartyCommand,
    ): Long {
        requireOwnerUserMatches(userId, user)
        val party =
            PaperOnlyParty(
                ownerId = userId,
                celebrantNickname = command.celebrantNickname,
                startedAt = command.startedDate.atStartOfDay(),
            )
        val saved = partyRepository.save(party)
        participantRepository.save(
            Participant(
                party = saved,
                user = user,
                isCelebrant = true,
            ),
        )
        return saved.id
    }

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

    fun findActiveRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val realtimeParty = requireRealtimePartyForChat(party)
        if (realtimeParty.status() != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return realtimeParty
    }

    fun requireRealtimeParty(partyId: Long): RealtimeParty {
        val party = findParty(partyId)
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }

    private fun requireRealtimePartyForChat(party: Party): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }

    private fun findParty(partyId: Long) =
        partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    private fun requireOwnerUserMatches(
        userId: Long,
        user: User,
    ) {
        if (userId != user.id) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }
}
