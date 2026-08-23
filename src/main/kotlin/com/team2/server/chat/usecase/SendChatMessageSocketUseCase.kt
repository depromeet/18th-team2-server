package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatMessagePersister
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendChatMessageSocketUseCase(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val chatMessagePersister: ChatMessagePersister,
    private val chatSocketGateway: ChatSocketGateway,
) {
    /**
     * WebSocket 채널의 메시지 전송.
     *
     * `/ws` 핸드셰이크는 게스트 입장을 허용하는 permitAll 이라 STOMP 프레임에는 인증 주체가 없다.
     * 그래서 WebSocket 입장 플로우와 동일하게 userId = null 로 두고 participantToken 으로만 신원을 확인한다.
     */
    @Transactional
    fun send(
        partyId: Long,
        participantToken: String,
        content: String,
    ): ChatMessageResponse {
        val party = resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, null, participantToken)

        val response = chatMessagePersister.persist(party, profile, content)
        chatSocketGateway.broadcastAfterCommit(partyId, "message", response)
        return response
    }
}
