package com.team2.server.chat.dto

/**
 * 모든 WebSocket 요청이 공통으로 싣는 최소 필드.
 *
 * 실패 통지는 요청 종류와 무관하게 clientRequestId 하나만 있으면 되고,
 * `@MessageExceptionHandler` 는 `@Payload` 타입이 아니라 예외 타입으로만 매핑되어
 * 요청 타입별로 여러 개를 둘 수 없다(같은 예외 타입에 두 개 이상 매핑되면 Ambiguous @ExceptionHandler
 * 로 실패한다. 기동 시점이 아니라 해당 컨트롤러에서 처음 예외가 발생해 매핑을 만드는 순간이다).
 * 그래서 실패 핸들러는 이 공통 봉투 타입으로 원본 프레임을 다시 변환한다.
 */
data class SocketRequestEnvelope(
    val clientRequestId: String,
)
