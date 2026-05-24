package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test

class CandleBlowSessionCleanupSchedulerTest {
    private val sessionStore: CandleBlowSessionStore = mock()
    private val scheduler = CandleBlowSessionCleanupScheduler(sessionStore, Clock.systemDefaultZone())

    @AfterTest
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun `만료된 촛불끄기 세션 정리를 store에 위임한다`() {
        val now = LocalDateTime.of(2026, 5, 24, 20, 15)

        scheduler.cleanup(now)

        verify(sessionStore).removeExpired(now)
    }
}
