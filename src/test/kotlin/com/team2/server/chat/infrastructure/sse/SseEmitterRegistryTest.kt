package com.team2.server.chat.infrastructure.sse

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `subscribe 후 complete하면 registry에서 제거됨`() {
        val emitter = SseEmitter(1000L)
        registry.subscribe(1L, emitter, "tok")
        assertEquals(1, registry.count(1L))

        emitter.complete()
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

        oldEmitter.complete()
        assertEquals(1, registry.count(1L))

        registry.unsubscribeByToken("tok")
        assertEquals(0, registry.count(1L))
    }
}
