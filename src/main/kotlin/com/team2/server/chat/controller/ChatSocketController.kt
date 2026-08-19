package com.team2.server.chat.controller

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartySocketRequest
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompSessionPartyRegistry
import com.team2.server.chat.usecase.EnterAndSubscribeChatSocketUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatSocketController(
    private val enterAndSubscribeChatSocketUseCase: EnterAndSubscribeChatSocketUseCase,
    private val stompSessionPartyRegistry: StompSessionPartyRegistry,
    private val chatSocketGateway: ChatSocketGateway,
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

    /**
     * 입장 실패를 에러 채널로 통지한다.
     *
     * 이 핸들러가 없으면 실패한 입장은 아무 프레임도 내려가지 않아 클라이언트가 무한정 대기한다.
     * `@Payload` 는 원본 메시지를 다시 변환해 clientRequestId 를 얻는다. JSON 자체가 깨진 경우에는
     * 변환이 다시 실패해 통지할 수 없고 Spring 이 서버 로그에만 남긴다(그 경우 주소를 알 방법이 없다).
     */
    @MessageExceptionHandler
    fun handleEnterFailure(
        exception: Exception,
        @Payload request: EnterRealtimePartySocketRequest,
    ) {
        val errorCode =
            when (exception) {
                is BusinessException -> exception.errorCode
                else -> ErrorCode.INTERNAL_SERVER_ERROR
            }
        log.warn(
            "WebSocket 입장 실패: clientRequestId={}, code={}",
            request.clientRequestId,
            errorCode.name,
            exception,
        )
        chatSocketGateway.sendError(request.clientRequestId, errorCode.name, errorCode.message)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocketController::class.java)
    }
}
