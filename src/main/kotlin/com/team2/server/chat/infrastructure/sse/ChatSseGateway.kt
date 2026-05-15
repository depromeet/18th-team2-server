package com.team2.server.chat.infrastructure.sse

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class ChatSseGateway(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    fun subscribe(
        partyId: Long,
        emitter: SseEmitter,
        participantToken: String,
    ) {
        sseEmitterRegistry.subscribe(partyId, emitter, participantToken)
    }

    fun broadcastAfterCommit(
        partyId: Long,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
        excludeToken: String? = null,
    ) {
        applicationEventPublisher.publishEvent(SseBroadcastEvent(partyId, event, excludeToken))
    }

    fun leave(participantToken: String) {
        sseEmitterRegistry.unsubscribeByToken(participantToken)
    }
}
