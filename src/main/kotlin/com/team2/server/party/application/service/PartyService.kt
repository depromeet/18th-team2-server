package com.team2.server.party.application.service

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val rollingPaperRepository: RollingPaperRepository,
) {
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

    fun markLiveStartedIfAbsent(
        partyId: Long,
        liveStartedAt: LocalDateTime,
    ): Boolean = partyRepository.markLiveStartedIfAbsent(partyId, liveStartedAt) == 1

    private fun findParty(partyId: Long) =
        partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
}
