package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SubscribeChatUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    fun subscribe(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): SseEmitter {
        val party = partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }

        resolveProfile(userId, participantToken, partyId)

        val emitter = SseEmitter(15 * 60 * 1000L)

        val history = chatMessageRepository.findAllByPartyIdOrderByCreatedAtAsc(partyId)
            .map { ChatMessageResponse.from(it) }

        try {
            emitter.send(SseEmitter.event().name("history").data(history).build())
        } catch (e: Exception) {
            emitter.completeWithError(e)
            return emitter
        }

        sseEmitterRegistry.subscribe(partyId, emitter)
        return emitter
    }

    private fun resolveProfile(
        userId: Long?,
        participantToken: String?,
        partyId: Long,
    ): RealtimeParticipantProfile {
        if (userId != null) {
            val participant = participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            return profileRepository.findByParticipant(participant)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        }

        if (participantToken != null) {
            val profile = profileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
            if (profile.participant.party.id != partyId) {
                throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            return profile
        }

        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}
