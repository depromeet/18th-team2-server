package com.team2.server.chat.infrastructure.websocket

import org.springframework.stereotype.Component

/**
 * STOMP CONNECT 시점에 인증된 userId를 세션 속성에 기록/조회한다.
 *
 * [StompJwtAuthenticationInterceptor]가 매 프레임 `StompHeaderAccessor.wrap()`으로 새로
 * 생성하는 accessor 객체에 `accessor.setUser(...)`를 호출해도, Spring이 CONNECT 시점의
 * 원본 accessor에만 등록해 둔 세션-사용자 캐시 콜백([StompSubProtocolHandler]의
 * `SessionInfo.setUserChangeCallback`)은 트리거되지 않아 이후 프레임에는 전파되지 않는다.
 * 대신 [StompSessionPartyRegistry]와 동일하게 WebSocket 세션 속성 맵(세션당 하나, 모든
 * accessor가 같은 참조를 공유)에 직접 기록해 세션 전체에 걸쳐 값을 유지한다.
 */
@Component
class StompSessionUserRegistry {
    fun markAuthenticated(
        sessionAttributes: MutableMap<String, Any>?,
        userId: Long,
    ) {
        sessionAttributes?.put(AUTHENTICATED_USER_ID, userId)
    }

    fun resolveUserId(sessionAttributes: Map<String, Any>?): Long? =
        sessionAttributes?.get(AUTHENTICATED_USER_ID) as? Long

    private companion object {
        const val AUTHENTICATED_USER_ID = "authenticatedUserId"
    }
}
