package com.team2.server.chat.infrastructure.sse

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class ChatSseGatewayTest {
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val sseEmitterRegistry: SseEmitterRegistry = mock()
    private val gateway = ChatSseGateway(applicationEventPublisher, sseEmitterRegistry)

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `subscribe delegates to registry`() {
        val emitter = SseEmitter()

        gateway.subscribe(1L, emitter, "tok")

        verify(sseEmitterRegistry).subscribe(1L, emitter, "tok")
    }

    @Test
    fun `broadcastAfterCommit broadcasts immediately without transaction synchronization`() {
        val event = event()

        gateway.broadcastAfterCommit(1L, event, excludeToken = "tok")

        verify(sseEmitterRegistry).broadcast(1L, event, "tok")
        verifyNoInteractions(applicationEventPublisher)
    }

    @Test
    fun `broadcastAfterCommit uses no excluded token by default`() {
        val event = event()

        gateway.broadcastAfterCommit(1L, event)

        verify(sseEmitterRegistry).broadcast(1L, event, null)
    }

    @Test
    fun `broadcastAfterCommit publishes event when transaction synchronization is active`() {
        val event = event()
        TransactionSynchronizationManager.initSynchronization()

        gateway.broadcastAfterCommit(1L, event, excludeToken = "tok")

        verify(applicationEventPublisher).publishEvent(SseBroadcastEvent(1L, event, "tok"))
    }

    @Test
    fun `leave delegates token unsubscribe`() {
        gateway.leave("tok")

        verify(sseEmitterRegistry).unsubscribeByToken("tok")
    }

    private fun event(): Set<ResponseBodyEmitter.DataWithMediaType> =
        SseEmitter
            .event()
            .name("message")
            .data("hello")
            .build()
}
