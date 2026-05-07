package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.ChatMessageBroadcastEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.entity.RealtimePartyStatus
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SendChatMessageUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun send(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ChatMessageResponse {
        val party = resolveActiveRealtimeParty(partyId)
        val profile = resolveProfile(userId, participantToken, party, partyId)

        val message =
            chatMessageRepository.save(
                ChatMessage(content = request.content, party = party, profile = profile),
            )

        val response = ChatMessageResponse.from(message)
        applicationEventPublisher.publishEvent(
            ChatMessageBroadcastEvent(
                partyId,
                SseEmitter
                    .event()
                    .name("message")
                    .data(response)
                    .build(),
            ),
        )
        return response
    }

    private fun resolveActiveRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        return toActiveLiveParty(party)
    }

    private fun toActiveLiveParty(party: Party): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        val realtimeParty = party as RealtimeParty
        if (realtimeParty.status() != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return realtimeParty
    }

    private fun resolveProfile(
        userId: Long?,
        participantToken: String?,
        party: RealtimeParty,
        partyId: Long,
    ): RealtimeParticipantProfile {
        if (userId != null) return resolveProfileByUserId(partyId, userId)
        if (participantToken != null) return resolveProfileByToken(participantToken, party, partyId)
        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }

    private fun resolveProfileByUserId(
        partyId: Long,
        userId: Long,
    ): RealtimeParticipantProfile {
        val participant =
            participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        return profileRepository.findByParticipant(participant)
            ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
    }

    private fun resolveProfileByToken(
        participantToken: String,
        party: RealtimeParty,
        partyId: Long,
    ): RealtimeParticipantProfile {
        val profile =
            profileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        val profileParty = profile.participant.party
        if (profileParty !== party && profileParty.id != partyId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        return profile
    }
}
