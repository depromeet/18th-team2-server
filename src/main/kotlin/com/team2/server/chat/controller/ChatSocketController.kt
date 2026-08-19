package com.team2.server.chat.controller

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartySocketRequest
import com.team2.server.chat.dto.LeaveChatSocketRequest
import com.team2.server.chat.dto.SendChatMessageSocketRequest
import com.team2.server.chat.dto.SocketRequestEnvelope
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompSessionPartyRegistry
import com.team2.server.chat.usecase.EnterAndSubscribeChatSocketUseCase
import com.team2.server.chat.usecase.LeaveChatSocketUseCase
import com.team2.server.chat.usecase.SendChatMessageSocketUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatSocketController(
    private val enterAndSubscribeChatSocketUseCase: EnterAndSubscribeChatSocketUseCase,
    private val sendChatMessageSocketUseCase: SendChatMessageSocketUseCase,
    private val leaveChatSocketUseCase: LeaveChatSocketUseCase,
    private val stompSessionPartyRegistry: StompSessionPartyRegistry,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @MessageMapping("/party-invites/{inviteToken}/realtime-participants")
    fun enterAndSubscribe(
        @DestinationVariable inviteToken: String,
        @Valid @Payload request: EnterRealtimePartySocketRequest,
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
        val payload =
            leaveChatSocketUseCase.leave(
                partyId = partyId,
                participantToken = request.participantToken,
                // 퇴장한 세션은 더 이상 이 파티의 브로드캐스트 토픽을 새로 구독할 수 없어야 한다.
                // 입장 경로의 onEntered 와 대칭으로, 브로드캐스트가 나가기 전에 인가를 회수한다.
                onLeft = { id -> stompSessionPartyRegistry.markLeft(headerAccessor.sessionAttributes, id) },
            )
        // REST 의 204 응답에 해당하는 개인 완료 신호.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "left", payload)
    }

    /**
     * 입장 / 메시지 전송 / 퇴장 실패를 에러 채널로 통지한다.
     *
     * 이 핸들러가 없으면 실패한 요청은 아무 프레임도 내려가지 않아 클라이언트가 무한정 대기한다.
     * `@Payload` 는 원본 메시지를 다시 변환해 clientRequestId 를 얻는다. JSON 자체가 깨졌거나
     * clientRequestId 가 없으면 변환이 다시 실패해 통지할 수 없고 Spring 이 서버 로그에만 남긴다
     * (그 경우 주소를 알 방법이 없다).
     *
     * 요청 타입별로 핸들러를 나눌 수는 없다. `@MessageExceptionHandler` 는 `@Payload` 타입이 아니라
     * 예외 타입으로만 매핑되므로, 같은 예외 타입을 처리하는 핸들러가 둘 이상이면
     * Ambiguous @ExceptionHandler 로 실패한다(기동 시점이 아니라 이 컨트롤러에서 처음 예외가 난 순간
     * 예외 핸들러 매핑을 만들면서 터진다 — 즉 실패 통지 자체가 사라진다).
     * 그래서 세 경로가 공통으로 싣는 [SocketRequestEnvelope] 로 한 번만 변환한다.
     */
    @MessageExceptionHandler
    fun handleSocketFailure(
        exception: Exception,
        @Payload envelope: SocketRequestEnvelope,
        message: Message<*>,
    ) {
        val errorCode =
            when (exception) {
                is BusinessException -> exception.errorCode
                is MethodArgumentNotValidException -> ErrorCode.INVALID_INPUT
                else -> ErrorCode.INTERNAL_SERVER_ERROR
            }
        log.warn(
            "WebSocket 요청 실패: destination={}, clientRequestId={}, code={}",
            SimpMessageHeaderAccessor.getDestination(message.headers),
            envelope.clientRequestId,
            errorCode.name,
            exception,
        )
        chatSocketGateway.sendError(envelope.clientRequestId, errorCode.name, errorCode.message)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSocketController::class.java)
    }
}
