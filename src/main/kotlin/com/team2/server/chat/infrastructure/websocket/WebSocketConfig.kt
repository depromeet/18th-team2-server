package com.team2.server.chat.infrastructure.websocket

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompJwtAuthenticationInterceptor: StompJwtAuthenticationInterceptor,
    private val stompDestinationAuthorizationInterceptor: StompDestinationAuthorizationInterceptor,
    @Qualifier("chatTaskScheduler") private val chatTaskScheduler: TaskScheduler,
) : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 하트비트가 없으면 끊긴 연결(네트워크 전환, 탭 백그라운드 등)을 TCP 타임아웃(수 분)까지
        // 서버·클라이언트 양쪽 모두 감지하지 못한다. 10초 간격으로 서로 살아있음을 확인해
        // 클라이언트가 재연결을 훨씬 빨리 시작할 수 있게 한다.
        registry
            .enableSimpleBroker("/topic")
            .setHeartbeatValue(longArrayOf(HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS))
            .setTaskScheduler(chatTaskScheduler)
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompJwtAuthenticationInterceptor, stompDestinationAuthorizationInterceptor)
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 10_000L
    }
}
