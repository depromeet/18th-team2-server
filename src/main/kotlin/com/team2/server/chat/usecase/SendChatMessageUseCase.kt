package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.ChatSseService
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import com.team2.server.party.infrastructure.CharacterImageResolver
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SendChatMessageUseCase(
    private val partyRepository: PartyRepository,
    private val profileService: RealtimeParticipantProfileService,
    private val chatMessageRepository: ChatMessageRepository,
    private val characterImageResolver: CharacterImageResolver,
    private val chatSseService: ChatSseService,
) {
    @Transactional
    fun send(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ChatMessageResponse {
        val party = resolveActiveRealtimeParty(partyId)
        val profile = profileService.resolveProfile(partyId, userId, participantToken)

        val message =
            chatMessageRepository.save(
                ChatMessage(content = request.content, party = party, profile = profile),
            )

        val imageUrl = message.profile.character?.let { characterImageResolver.resolve(it) }
        val response = ChatMessageResponse.from(message, message.profile.participant.isCelebrant, imageUrl)
        chatSseService.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("message")
                .data(response)
                .build(),
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
        val realtimeParty = Hibernate.unproxy(party) as RealtimeParty
        if (realtimeParty.status() != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return realtimeParty
    }
}
