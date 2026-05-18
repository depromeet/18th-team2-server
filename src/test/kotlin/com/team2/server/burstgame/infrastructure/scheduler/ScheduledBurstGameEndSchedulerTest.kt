package com.team2.server.burstgame.infrastructure.scheduler

import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ScheduledBurstGameEndSchedulerTest {
    private val scheduler = ScheduledBurstGameEndScheduler()

    @AfterTest
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun `종료 시간이 되면 callback을 실행한다`() {
        val latch = CountDownLatch(1)

        scheduler.schedule("round-1", LocalDateTime.now().plusNanos(1)) { roundId ->
            if (roundId == "round-1") {
                latch.countDown()
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `callback 예외는 스케줄러 밖으로 전파하지 않는다`() {
        scheduler.schedule("round-1", LocalDateTime.now().plusNanos(1)) {
            throw IllegalStateException("boom")
        }

        Thread.sleep(100)
    }
}
