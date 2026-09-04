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

    /**
     * 입장 실패를 클라이언트에 알린다.
     *
     * 입장 자체가 실패하면(만료된 초대, 종료된 파티, 없는 캐릭터 등) partyId 를 알 수 없어
     * 개인 ack 채널(/topic/parties/{partyId}/personal/{clientRequestId})을 쓸 수 없다.
     * 그래서 clientRequestId 만으로 주소가 정해지는 별도 에러 채널을 사용한다.
     */
    fun sendError(
        clientRequestId: String,
        code: String,
        message: String,
    ) {
        messagingTemplate.convertAndSend(
            "/topic/errors/$clientRequestId",
            SocketEventMessage("error", SocketErrorPayload(code, message)),
        )
    }

    fun sendPersonalAfterCommit(
        partyId: Long,
        clientRequestId: String,
        eventName: String,
        payload: Any,
    ) {
        val event = SocketPersonalEvent(partyId, clientRequestId, eventName, payload)
        if (deliversAfterCommit()) {
            applicationEventPublisher.publishEvent(event)
        } else {
            sendPersonal(event.partyId, event.clientRequestId, event.eventName, event.payload)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPersonal(event: SocketPersonalEvent) {
        sendPersonal(event.partyId, event.clientRequestId, event.eventName, event.payload)
    }

    fun broadcastAfterCommit(
        partyId: Long,
        eventName: String,
        payload: Any,
    ) {
        val event = SocketBroadcastEvent(partyId, eventName, payload)
        if (deliversAfterCommit()) {
            applicationEventPublisher.publishEvent(event)
        } else {
            broadcast(event)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBroadcast(event: SocketBroadcastEvent) {
        broadcast(event)
    }

    private fun deliversAfterCommit(): Boolean =
        TransactionSynchronizationManager.isSynchronizationActive() &&
            TransactionSynchronizationManager.isActualTransactionActive()

    private fun broadcast(event: SocketBroadcastEvent) {
        messagingTemplate.convertAndSend(
            "/topic/parties/${event.partyId}",
            SocketEventMessage(event.eventName, event.payload),
        )
    }
}
