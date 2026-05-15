package com.team2.server.chat.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class ChatSseService(
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
    ) {
        applicationEventPublisher.publishEvent(SseBroadcastEvent(partyId, event))
    }

    fun leave(participantToken: String) {
        sseEmitterRegistry.unsubscribeByToken(participantToken)
    }
}
