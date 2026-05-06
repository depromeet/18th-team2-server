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
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SubscribeChatUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    @Transactional(readOnly = true)
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

        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)

        val history = chatMessageRepository.findAllByPartyIdOrderByCreatedAtAsc(partyId)
            .map { ChatMessageResponse.from(it) }

        try {
            emitter.send(SseEmitter.event().name("history").data(history).build())
        } catch (e: IllegalStateException) {
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
            return resolveProfileByUserId(partyId, userId)
        }
        if (participantToken != null) {
            return resolveProfileByToken(participantToken, partyId)
        }
        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }

    private fun resolveProfileByUserId(partyId: Long, userId: Long): RealtimeParticipantProfile {
        val participant = participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        return profileRepository.findByParticipant(participant)
            ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
    }

    private fun resolveProfileByToken(participantToken: String, partyId: Long): RealtimeParticipantProfile {
        val profile = profileRepository.findByParticipantToken(participantToken)
            ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        if (profile.participant.party.id != partyId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        return profile
    }

    companion object {
        private const val EMITTER_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
