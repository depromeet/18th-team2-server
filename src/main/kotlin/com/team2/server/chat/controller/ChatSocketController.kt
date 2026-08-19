package com.team2.server.chat.controller

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartySocketRequest
import com.team2.server.chat.infrastructure.websocket.StompSessionPartyRegistry
import com.team2.server.chat.usecase.EnterAndSubscribeChatSocketUseCase
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatSocketController(
    private val enterAndSubscribeChatSocketUseCase: EnterAndSubscribeChatSocketUseCase,
    private val stompSessionPartyRegistry: StompSessionPartyRegistry,
) {
    @MessageMapping("/party-invites/{inviteToken}/realtime-participants")
    fun enterAndSubscribe(
        @DestinationVariable inviteToken: String,
        @Payload request: EnterRealtimePartySocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        enterAndSubscribeChatSocketUseCase.enterAndSubscribe(
            inviteToken = inviteToken,
            userId = null,
            request = EnterRealtimePartyRequest(request.nickname, request.characterId, request.participantToken),
            clientRequestId = request.clientRequestId,
            // 입장에 성공한 세션만 해당 파티의 브로드캐스트 토픽을 구독할 수 있도록 세션에 기록한다.
            onEntered = { partyId -> stompSessionPartyRegistry.markEntered(headerAccessor.sessionAttributes, partyId) },
        )
    }
}
