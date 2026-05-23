package com.team2.server.chat.infrastructure.sse

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.assertEquals

class SseEmitterRegistryTest {
    private lateinit var registry: SseEmitterRegistry

    @BeforeEach
    fun setUp() {
        registry = SseEmitterRegistry()
    }

    @Test
    fun `등록된 emitter가 없으면 broadcast 시 아무 일도 없음`() {
        registry.broadcast(
            1L,
            SseEmitter
                .event()
                .name("message")
                .data("hello")
                .build(),
        )
    }

    @Test
    fun `completeAll 호출 시 registry에서 제거됨`() {
        val emitter = SseEmitter(1000L)
        registry.subscribe(1L, emitter, "tok")
        assertEquals(1, registry.count(1L))

        registry.completeAll(1L)
        assertEquals(0, registry.count(1L))
    }

    @Test
    fun `여러 emitter 등록 후 count 확인`() {
        val emitter1 = SseEmitter(5000L)
        val emitter2 = SseEmitter(5000L)

        registry.subscribe(1L, emitter1, "tok1")
        registry.subscribe(1L, emitter2, "tok2")
        assertEquals(2, registry.count(1L))
    }

    @Test
    fun `다른 partyId의 emitter는 독립적`() {
        registry.subscribe(1L, SseEmitter(5000L), "tok1")
        registry.subscribe(2L, SseEmitter(5000L), "tok2")

        assertEquals(1, registry.count(1L))
        assertEquals(1, registry.count(2L))
    }

    @Test
    fun `같은 토큰으로 재입장해도 이전 emitter 정리가 새 매핑을 지우지 않음`() {
        val oldEmitter = SseEmitter(5000L)
        val newEmitter = SseEmitter(5000L)

        registry.subscribe(1L, oldEmitter, "tok")
        registry.subscribe(1L, newEmitter, "tok")

        assertEquals(1, registry.count(1L))
        assertEquals(1, registry.count(1L))

        registry.unsubscribeByToken("tok")
        assertEquals(0, registry.count(1L))
    }

    @Test
    fun `broadcast 실패 emitter는 토큰 매핑도 정리됨`() {
        val emitter = FailingSseEmitter()
        registry.subscribe(1L, emitter, "tok")

        registry.broadcast(
            1L,
            SseEmitter
                .event()
                .name("message")
                .data("hello")
                .build(),
        )
        registry.unsubscribeByToken("tok")

        assertEquals(0, registry.count(1L))
        assertEquals(0, emitter.completeCount)
    }

    @Test
    fun `excludeToken emitter에는 broadcast 하지 않는다`() {
        val excluded = RecordingSseEmitter()
        val included = RecordingSseEmitter()
        registry.subscribe(1L, excluded, "tok1")
        registry.subscribe(1L, included, "tok2")

        registry.broadcast(1L, event(), excludeToken = "tok1")

        assertEquals(0, excluded.sendCount)
        assertEquals(1, included.sendCount)
    }

    @Test
    fun `broadcastHost는 host emitter에만 전송한다`() {
        val host = RecordingSseEmitter()
        val participant = RecordingSseEmitter()
        registry.subscribe(1L, host, "host", isHost = true)
        registry.subscribe(1L, participant, "participant", isHost = false)

        registry.broadcastHost(1L, event())

        assertEquals(1, host.sendCount)
        assertEquals(0, participant.sendCount)
    }

    @Test
    fun `broadcastHost 실패 emitter는 일반 목록에서도 제거한다`() {
        val host = FailingSseEmitter()
        registry.subscribe(1L, host, "host", isHost = true)

        registry.broadcastHost(1L, event())

        assertEquals(0, registry.count(1L))
    }

    @Test
    fun `onBroadcast delegates to broadcast`() {
        val emitter = RecordingSseEmitter()
        val event = event()
        registry.subscribe(1L, emitter, "tok")

        registry.onBroadcast(SseBroadcastEvent(1L, event, excludeToken = null))

        assertEquals(1, emitter.sendCount)
    }

    private fun event(): Set<ResponseBodyEmitter.DataWithMediaType> =
        SseEmitter
            .event()
            .name("message")
            .data("hello")
            .build()

    private class RecordingSseEmitter : SseEmitter(5000L) {
        var sendCount = 0
            private set

        override fun send(dataSet: Set<ResponseBodyEmitter.DataWithMediaType>) {
            sendCount += 1
        }
    }

    private class FailingSseEmitter : SseEmitter(5000L) {
        var completeCount = 0
            private set

        override fun send(dataSet: Set<ResponseBodyEmitter.DataWithMediaType>): Unit =
            throw IllegalStateException("closed")

        override fun complete() {
            completeCount += 1
            super.complete()
        }
    }
}
