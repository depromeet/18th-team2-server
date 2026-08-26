package com.team2.server.chat.infrastructure.websocket

import com.team2.server.chat.dto.SocketRequestEnvelope
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.web.bind.annotation.ControllerAdvice

/**
 * 모든 STOMP `@MessageMapping` 컨트롤러(채팅, 촛불끄기, 박터뜨리기, 폭죽 등)에 공통 적용되는
 * 실패 통지 핸들러. 컨트롤러별로 중복 정의하지 않도록 `@ControllerAdvice`로 전역 등록한다.
 *
 * 이 핸들러가 없으면 실패한 요청은 아무 프레임도 내려가지 않아 클라이언트가 무한정 대기한다.
 * `@Payload` 는 원본 메시지를 다시 변환해 clientRequestId 를 얻는다. JSON 자체가 깨졌거나
 * clientRequestId 가 없으면 변환이 다시 실패해 통지할 수 없고 Spring 이 서버 로그에만 남긴다
 * (그 경우 주소를 알 방법이 없다).
 *
 * 요청 타입별로 핸들러를 나눌 수는 없다. `@MessageExceptionHandler` 는 `@Payload` 타입이 아니라
 * 예외 타입으로만 매핑되므로, 같은 예외 타입을 처리하는 핸들러가 둘 이상이면
 * Ambiguous @ExceptionHandler 로 실패한다. 그래서 모든 WS 요청이 공통으로 싣는
 * [SocketRequestEnvelope] 로 한 번만 변환한다.
 */
@ControllerAdvice
class StompSocketExceptionHandler(
    private val chatSocketGateway: ChatSocketGateway,
) {
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

    private companion object {
        val log = LoggerFactory.getLogger(StompSocketExceptionHandler::class.java)
    }
}
