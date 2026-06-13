package com.team2.server.chat.infrastructure.sse

import com.team2.server.chat.application.port.PartySseEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class ChatSseGateway(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val sseEmitterRegistry: SseEmitterRegistry,
) : PartySseEventPublisher {
    fun subscribe(
        partyId: Long,
        emitter: SseEmitter,
        participantToken: String,
    ) {
        sseEmitterRegistry.subscribe(partyId, emitter, participantToken)
    }

    override fun broadcastAfterCommit(
        partyId: Long,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
        excludeToken: String?,
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            applicationEventPublisher.publishEvent(SseBroadcastEvent(partyId, event, excludeToken))
        } else {
            sseEmitterRegistry.broadcast(partyId, event, excludeToken)
        }
    }

    fun leave(participantToken: String) {
        sseEmitterRegistry.unsubscribeByToken(participantToken)
    }
}
