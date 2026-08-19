package com.team2.server.chat.infrastructure.websocket

import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class ChatSocketGateway(
    private val messagingTemplate: SimpMessagingTemplate,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun sendPersonal(
        partyId: Long,
        clientRequestId: String,
        eventName: String,
        payload: Any,
    ) {
        messagingTemplate.convertAndSend(
            "/topic/parties/$partyId/personal/$clientRequestId",
            SocketEventMessage(eventName, payload),
        )
    }

    fun broadcastAfterCommit(
        partyId: Long,
        eventName: String,
        payload: Any,
    ) {
        val event = SocketBroadcastEvent(partyId, eventName, payload)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            applicationEventPublisher.publishEvent(event)
        } else {
            broadcast(event)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBroadcast(event: SocketBroadcastEvent) {
        broadcast(event)
    }

    private fun broadcast(event: SocketBroadcastEvent) {
        messagingTemplate.convertAndSend(
            "/topic/parties/${event.partyId}",
            SocketEventMessage(event.eventName, event.payload),
        )
    }
}
