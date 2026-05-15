package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SendChatMessageUseCase(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
    private val chatSseGateway: ChatSseGateway,
) {
    @Transactional
    fun send(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ChatMessageResponse {
        val party = resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)

        val message =
            chatMessageRepository.save(
                ChatMessage(content = request.content, party = party, profile = profile),
            )

        val imageUrl =
            message.profile.character?.id?.let {
                imageUrlReader.findFirstImageUrlByTargetIds(ImageTargetType.CHARACTER, listOf(it))[it]
            }
        val response = ChatMessageResponse.from(message, message.profile.participant.isCelebrant, imageUrl)
        chatSseGateway.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("message")
                .data(response)
                .build(),
        )
        return response
    }
}
