package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test

class BurstGameSessionCleanupSchedulerTest {
    private val sessionStore: BurstGameSessionStore = mock()
    private val scheduler = BurstGameSessionCleanupScheduler(sessionStore)

    @AfterTest
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun `만료된 세션 정리를 store에 위임한다`() {
        val now = LocalDateTime.of(2026, 5, 18, 14, 10)

        scheduler.cleanup(now)

        verify(sessionStore).removeExpired(now)
    }
}
