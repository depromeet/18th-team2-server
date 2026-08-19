package com.team2.server.chat.infrastructure.websocket

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StompSessionPartyRegistryTest {
    private val registry = StompSessionPartyRegistry()

    @Test
    fun `입장한 파티만 hasEntered 가 true 다`() {
        val attributes = mutableMapOf<String, Any>()

        registry.markEntered(attributes, 1L)

        assertTrue(registry.hasEntered(attributes, 1L))
        assertFalse(registry.hasEntered(attributes, 2L))
    }

    @Test
    fun `퇴장하면 해당 파티의 구독 인가가 회수된다`() {
        val attributes = mutableMapOf<String, Any>()
        registry.markEntered(attributes, 1L)
        registry.markEntered(attributes, 2L)

        registry.markLeft(attributes, 1L)

        assertFalse(registry.hasEntered(attributes, 1L), "퇴장한 파티는 더 이상 구독할 수 없어야 한다")
        assertTrue(registry.hasEntered(attributes, 2L), "다른 파티의 입장 기록은 유지되어야 한다")
    }

    @Test
    fun `입장 기록이 없어도 markLeft 는 예외 없이 처리된다`() {
        val attributes = mutableMapOf<String, Any>()

        registry.markLeft(attributes, 1L)
        registry.markLeft(null, 1L)

        assertFalse(registry.hasEntered(attributes, 1L))
    }
}
