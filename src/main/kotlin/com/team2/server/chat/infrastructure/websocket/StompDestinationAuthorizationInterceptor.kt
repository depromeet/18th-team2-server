package com.team2.server.chat.infrastructure.websocket

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

/**
 * STOMP 인바운드 채널에서 목적지(destination) 단위 인가를 수행한다.
 *
 * `/ws` 핸드셰이크는 게스트 입장을 허용해야 하므로 `permitAll` 이다(SSE 엔드포인트와 동일한 인증 모델).
 * 그래서 STOMP 레벨에서 다음 두 가지를 막지 않으면 누구나 남의 파티 프레임을 수신할 수 있다.
 *
 * 1. 와일드카드 구독 — SimpleBroker 의 DefaultSubscriptionRegistry 는 클라이언트가 보낸 SUBSCRIBE
 *    destination 을 Ant 패턴으로 취급해 매칭한다. 따라서 파티 토픽 하위를 통째로 가리키는
 *    와일드카드 구독 한 번으로 모든 파티의 브로드캐스트와 개인 ack(participantToken 포함)을
 *    수집할 수 있다.
 * 2. 브로커 목적지로의 직접 SEND — SimpleBroker 는 발신자를 구분하지 않으므로 클라이언트가
 *    브로드캐스트 토픽으로 직접 SEND 하면 해당 파티 전원에게 위조 이벤트가 전달된다.
 *    클라이언트는 애플리케이션 목적지(/app 접두사)로만 SEND 할 수 있어야 한다.
 */
@Component
class StompDestinationAuthorizationInterceptor(
    private val stompSessionPartyRegistry: StompSessionPartyRegistry,
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        when (accessor.command) {
            StompCommand.SUBSCRIBE -> verifySubscribe(accessor)
            StompCommand.SEND -> verifySend(accessor)
            else -> Unit
        }
        return message
    }

    private fun verifySend(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: reject("destination 헤더가 없습니다")
        if (destination.startsWith(BROKER_PREFIX)) {
            reject("브로커 목적지로 직접 전송할 수 없습니다")
        }
    }

    private fun verifySubscribe(accessor: StompHeaderAccessor) {
        val destination = accessor.destination ?: reject("destination 헤더가 없습니다")

        // Ant/URI 템플릿 메타문자가 포함된 구독은 형태를 따지지 않고 전부 거부한다.
        if (destination.any { it in PATTERN_METACHARACTERS }) {
            reject("와일드카드 구독은 허용되지 않습니다")
        }

        BROADCAST_DESTINATION.matchEntire(destination)?.let { match ->
            val partyId = match.groupValues[1].toLongOrNull() ?: reject("잘못된 파티 식별자입니다")
            if (!stompSessionPartyRegistry.hasEntered(accessor.sessionAttributes, partyId)) {
                reject("입장하지 않은 파티는 구독할 수 없습니다")
            }
            return
        }

        // 개인 ack 채널은 입장 응답을 놓치지 않으려면 SEND 이전에 구독해야 하므로 입장 여부로 막을 수 없다.
        // 대신 clientRequestId 를 정규 UUID 로 강제해 추측 불가능하게 만든다.
        if (PERSONAL_DESTINATION.matches(destination) || ERROR_DESTINATION.matches(destination)) {
            return
        }

        reject("허용되지 않은 구독 목적지입니다")
    }

    private fun reject(reason: String): Nothing = throw MessagingException("STOMP 구독/전송이 거부되었습니다: $reason")

    companion object {
        private const val BROKER_PREFIX = "/topic"
        private val PATTERN_METACHARACTERS = charArrayOf('*', '?', '{', '}')
        private const val UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

        private val BROADCAST_DESTINATION = Regex("""^/topic/parties/(\d{1,19})$""")
        private val PERSONAL_DESTINATION = Regex("""^/topic/parties/\d{1,19}/personal/$UUID_PATTERN$""")
        private val ERROR_DESTINATION = Regex("""^/topic/errors/$UUID_PATTERN$""")
    }
}
