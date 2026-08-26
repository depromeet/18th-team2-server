package com.team2.server.chat.infrastructure.websocket

import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.user.repository.UserRepository
import io.jsonwebtoken.JwtException
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

private const val BEARER_PREFIX = "Bearer "

/**
 * STOMP CONNECT 프레임에 실린 JWT로 세션의 인증 상태를 결정한다.
 *
 * `/ws` 핸드셰이크는 게스트 입장을 허용해야 하므로 `Authorization` 헤더가 없으면 익명으로 통과시킨다
 * (SSE 엔드포인트의 `JwtAuthenticationFilter`와 동일한 모델). 헤더가 있는데 유효하지 않으면 CONNECT
 * 자체를 거부한다 — 통과시키면 클라이언트가 인증됐다고 오인한 채로 게스트 취급받는다.
 *
 * 인증된 userId는 [StompSessionUserRegistry]를 통해 세션 속성에 기록한다 (이유는 해당 클래스 문서 참고).
 */
@Component
class StompJwtAuthenticationInterceptor(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    private val stompSessionUserRegistry: StompSessionUserRegistry,
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        if (accessor.command == StompCommand.CONNECT) authenticate(accessor)
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER) ?: return
        if (!header.startsWith(BEARER_PREFIX)) reject("Authorization 헤더 형식이 올바르지 않습니다")
        val token = header.removePrefix(BEARER_PREFIX).trim()

        val userId =
            try {
                jwtTokenProvider.parse(token).subject.toLong()
            } catch (e: JwtException) {
                reject("유효하지 않은 토큰입니다", e)
            } catch (e: IllegalArgumentException) {
                reject("유효하지 않은 토큰입니다", e)
            }
        if (!userRepository.existsById(userId)) reject("사용자를 찾을 수 없습니다")

        stompSessionUserRegistry.markAuthenticated(accessor.sessionAttributes, userId)
    }

    private fun reject(
        reason: String,
        cause: Throwable? = null,
    ): Nothing = throw MessagingException("STOMP 연결이 거부되었습니다: $reason", cause)

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
    }
}
