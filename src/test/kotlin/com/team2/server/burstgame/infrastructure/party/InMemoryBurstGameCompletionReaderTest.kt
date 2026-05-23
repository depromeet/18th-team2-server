package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.domain.BurstGameSession
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryBurstGameCompletionReaderTest {
    private val sessionStore: BurstGameSessionStore = mock()
    private val reader = InMemoryBurstGameCompletionReader(sessionStore)
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)

    @Test
    fun `returns true when active session is past endsAt`() {
        val session =
            BurstGameSession(
                partyId = 1L,
                startedAt = now.minusMinutes(1),
                endsAt = now.minusSeconds(1),
            )
        whenever(sessionStore.findByPartyId(1L, now)).thenReturn(session)

        assertTrue(reader.isCompleted(1L, now))
    }

    @Test
    fun `returns false when active session is not ended`() {
        val session =
            BurstGameSession(
                partyId = 1L,
                startedAt = now.minusSeconds(10),
                endsAt = now.plusSeconds(10),
            )
        whenever(sessionStore.findByPartyId(1L, now)).thenReturn(session)

        assertFalse(reader.isCompleted(1L, now))
    }
}
