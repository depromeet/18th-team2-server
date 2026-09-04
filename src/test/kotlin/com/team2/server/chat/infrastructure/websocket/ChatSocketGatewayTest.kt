package com.team2.server.chat.infrastructure.websocket

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

class ChatSocketGatewayTest {
    private val messagingTemplate: SimpMessagingTemplate = mock()
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val gateway = ChatSocketGateway(messagingTemplate, applicationEventPublisher)

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TransactionSynchronizationManager.setActualTransactionActive(false)
    }

    @Test
    fun `sendPersonalAfterCommit 는 동기화가 없으면 즉시 보낸다`() {
        gateway.sendPersonalAfterCommit(1L, "req-1", "entered", "payload")

        verify(messagingTemplate).convertAndSend(
            "/topic/parties/1/personal/req-1",
            SocketEventMessage("entered", "payload"),
        )
        verifyNoInteractions(applicationEventPublisher)
    }

    @Test
    fun `sendPersonalAfterCommit 는 동기화만 있고 실제 트랜잭션이 없으면 즉시 보낸다`() {
        TransactionSynchronizationManager.initSynchronization()

        gateway.sendPersonalAfterCommit(1L, "req-1", "entered", "payload")

        verify(messagingTemplate).convertAndSend(
            "/topic/parties/1/personal/req-1",
            SocketEventMessage("entered", "payload"),
        )
        verifyNoInteractions(applicationEventPublisher)
    }

    @Test
    fun `sendPersonalAfterCommit 는 실제 트랜잭션이 있으면 이벤트로 미룬다`() {
        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(true)

        gateway.sendPersonalAfterCommit(1L, "req-1", "entered", "payload")

        verify(applicationEventPublisher).publishEvent(SocketPersonalEvent(1L, "req-1", "entered", "payload"))
        verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `커밋 이후 미뤄둔 개인 ack 를 개인 채널로 보낸다`() {
        gateway.onPersonal(SocketPersonalEvent(1L, "req-1", "entered", "payload"))

        verify(messagingTemplate).convertAndSend(
            "/topic/parties/1/personal/req-1",
            SocketEventMessage("entered", "payload"),
        )
    }
}
