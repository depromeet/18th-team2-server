package com.team2.server.chat.controller

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartySocketRequest
import com.team2.server.chat.dto.LeaveChatSocketRequest
import com.team2.server.chat.dto.SendChatMessageSocketRequest
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompPartyPresenceRegistry
import com.team2.server.chat.infrastructure.websocket.StompSessionPartyRegistry
import com.team2.server.chat.infrastructure.websocket.StompSessionUserRegistry
import com.team2.server.chat.usecase.EnterAndSubscribeChatSocketUseCase
import com.team2.server.chat.usecase.LeaveChatSocketUseCase
import com.team2.server.chat.usecase.SendChatMessageSocketUseCase
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatSocketController(
    private val enterAndSubscribeChatSocketUseCase: EnterAndSubscribeChatSocketUseCase,
    private val sendChatMessageSocketUseCase: SendChatMessageSocketUseCase,
    private val leaveChatSocketUseCase: LeaveChatSocketUseCase,
    private val stompSessionPartyRegistry: StompSessionPartyRegistry,
    private val stompSessionUserRegistry: StompSessionUserRegistry,
    private val stompPartyPresenceRegistry: StompPartyPresenceRegistry,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @MessageMapping("/party-invites/{inviteToken}/realtime-participants")
    fun enterAndSubscribe(
        @DestinationVariable inviteToken: String,
        @Valid @Payload request: EnterRealtimePartySocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val sessionId = requireNotNull(headerAccessor.sessionId) { "STOMP 세션 id 가 없습니다" }
        enterAndSubscribeChatSocketUseCase.enterAndSubscribe(
            inviteToken = inviteToken,
            userId = stompSessionUserRegistry.resolveUserId(headerAccessor.sessionAttributes),
            request = EnterRealtimePartyRequest(request.nickname, request.characterId, request.participantToken),
            clientRequestId = request.clientRequestId,
            onEntered = { partyId, participantToken ->
                // 입장에 성공한 세션만 해당 파티의 브로드캐스트 토픽을 구독할 수 있도록 세션에 기록한다.
                stompSessionPartyRegistry.markEntered(headerAccessor.sessionAttributes, partyId)
                // `/participants` 조회가 참조하는 온라인 상태를 등록한다.
                stompPartyPresenceRegistry.markOnline(sessionId, partyId, participantToken)
            },
        )
    }

    @MessageMapping("/parties/{partyId}/chat-messages")
    fun sendMessage(
        @DestinationVariable partyId: Long,
        @Valid @Payload request: SendChatMessageSocketRequest,
    ) {
        val response = sendChatMessageSocketUseCase.send(partyId, request.participantToken, request.content)
        // REST 의 201 응답에 해당하는 개인 완료 신호. 없으면 발신자는 성공·실패를 구분할 수 없다.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "message-sent", response)
    }

    @MessageMapping("/parties/{partyId}/leave")
    fun leave(
        @DestinationVariable partyId: Long,
        @Valid @Payload request: LeaveChatSocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val sessionId = requireNotNull(headerAccessor.sessionId) { "STOMP 세션 id 가 없습니다" }
        val payload =
            leaveChatSocketUseCase.leave(
                partyId = partyId,
                participantToken = request.participantToken,
                onLeft = { id ->
                    // 퇴장한 세션은 더 이상 이 파티의 브로드캐스트 토픽을 새로 구독할 수 없어야 한다.
                    // 입장 경로의 onEntered 와 대칭으로, 브로드캐스트가 나가기 전에 인가를 회수한다.
                    stompSessionPartyRegistry.markLeft(headerAccessor.sessionAttributes, id)
                    stompPartyPresenceRegistry.markOffline(sessionId, id, request.participantToken)
                },
            )
        // REST 의 204 응답에 해당하는 개인 완료 신호.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "left", payload)
    }
}
