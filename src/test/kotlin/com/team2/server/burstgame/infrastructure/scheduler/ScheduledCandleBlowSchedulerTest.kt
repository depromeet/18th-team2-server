package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.usecase.EndScheduledCandleBlowUseCase
import com.team2.server.burstgame.application.usecase.RecoverCandleBlowScheduleUseCase
import com.team2.server.burstgame.application.usecase.StartScheduledCandleBlowUseCase
import com.team2.server.burstgame.config.CandleBlowProperties
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduledCandleBlowSchedulerTest {
    private val clock: Clock = Clock.systemDefaultZone()
    private val recoverCandleBlowScheduleUseCase: RecoverCandleBlowScheduleUseCase = mock()
    private val startScheduledCandleBlowUseCase: StartScheduledCandleBlowUseCase = mock()
    private val endScheduledCandleBlowUseCase: EndScheduledCandleBlowUseCase = mock()
    private val scheduler =
        ScheduledCandleBlowScheduler(
            clock = clock,
            recoverCandleBlowScheduleUseCase = recoverCandleBlowScheduleUseCase,
            startScheduledCandleBlowUseCase = startScheduledCandleBlowUseCase,
            endScheduledCandleBlowUseCase = endScheduledCandleBlowUseCase,
            candleBlowProperties = CandleBlowProperties(),
        )

    @AfterTest
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun `시작 시간이 되면 start callback을 실행한다`() {
        val latch = CountDownLatch(1)

        scheduler.scheduleStart(1L, LocalDateTime.now(clock).plus(SCHEDULE_DELAY)) { partyId ->
            if (partyId == 1L) {
                latch.countDown()
            }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `종료 시간이 되면 end callback을 실행한다`() {
        val latch = CountDownLatch(1)

        scheduler.scheduleEnd(1L, LocalDateTime.now(clock).plus(SCHEDULE_DELAY)) { partyId ->
            if (partyId == 1L) {
                latch.countDown()
            }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `예약된 작업을 취소한다`() {
        val latch = CountDownLatch(1)

        scheduler.scheduleStart(1L, LocalDateTime.now(clock).plusSeconds(1)) {
            latch.countDown()
        }

        assertTrue(scheduler.cancel(1L))
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS))
    }

    private companion object {
        val SCHEDULE_DELAY: Duration = Duration.ofMillis(250)
    }
}
