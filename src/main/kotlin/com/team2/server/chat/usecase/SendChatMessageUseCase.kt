package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatMessagePersister
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SendChatMessageUseCase(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val chatMessagePersister: ChatMessagePersister,
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

        val response = chatMessagePersister.persist(party, profile, request.content)
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
