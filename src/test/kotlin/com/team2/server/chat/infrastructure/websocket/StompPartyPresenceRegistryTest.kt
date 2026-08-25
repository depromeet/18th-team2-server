package com.team2.server.chat.infrastructure.websocket

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StompPartyPresenceRegistryTest {
    private val registry = StompPartyPresenceRegistry()

    @Test
    fun `입장하면 해당 파티의 온라인 참여자 토큰에 포함된다`() {
        registry.markOnline("session-1", 1L, "token-a")

        assertEquals(setOf("token-a"), registry.findOnlineParticipantTokens(1L))
    }

    @Test
    fun `다른 파티의 온라인 목록에는 영향을 주지 않는다`() {
        registry.markOnline("session-1", 1L, "token-a")

        assertTrue(registry.findOnlineParticipantTokens(2L).isEmpty())
    }

    @Test
    fun `퇴장하면 온라인 목록에서 제거된다`() {
        registry.markOnline("session-1", 1L, "token-a")

        registry.markOffline("session-1", 1L, "token-a")

        assertTrue(registry.findOnlineParticipantTokens(1L).isEmpty())
    }

    @Test
    fun `세션 연결이 끊기면 그 세션이 입장했던 파티의 온라인 목록에서 제거된다`() {
        registry.markOnline("session-1", 1L, "token-a")
        registry.markOnline("session-2", 1L, "token-b")

        registry.markSessionDisconnected("session-1")

        assertEquals(setOf("token-b"), registry.findOnlineParticipantTokens(1L))
    }

    @Test
    fun `기록 없는 세션이나 파티에 대해서도 예외 없이 처리된다`() {
        registry.markOffline("session-1", 1L, "token-a")
        registry.markSessionDisconnected("session-1")

        assertTrue(registry.findOnlineParticipantTokens(1L).isEmpty())
    }
}
