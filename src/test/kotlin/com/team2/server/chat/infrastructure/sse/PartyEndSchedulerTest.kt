package com.team2.server.chat.infrastructure.sse

import com.team2.server.party.application.event.RealtimePartyEndingStartedEvent
import com.team2.server.party.application.usecase.RecoverRealtimePartyEndScheduleUseCase
import com.team2.server.party.application.usecase.StartAutomaticRealtimePartyEndUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ScheduledFuture
import kotlin.test.assertEquals

class PartyEndSchedulerTest {
    private val taskScheduler: TaskScheduler = mock()
    private val sseEmitterRegistry: SseEmitterRegistry = mock()
    private val recoverRealtimePartyEndScheduleUseCase: RecoverRealtimePartyEndScheduleUseCase = mock()
    private val startAutomaticRealtimePartyEndUseCase: StartAutomaticRealtimePartyEndUseCase = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 18, 14, 20)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val scheduledTasks = mutableListOf<Runnable>()
    private val scheduledFutures = mutableListOf<ScheduledFuture<*>>()

    private lateinit var scheduler: PartyEndScheduler

    @BeforeEach
    fun setUp() {
        scheduler =
            PartyEndScheduler(
                taskScheduler = taskScheduler,
                sseEmitterRegistry = sseEmitterRegistry,
                recoverRealtimePartyEndScheduleUseCase = recoverRealtimePartyEndScheduleUseCase,
                startAutomaticRealtimePartyEndUseCase = startAutomaticRealtimePartyEndUseCase,
                clock = clock,
            )
        whenever(taskScheduler.schedule(any<Runnable>(), any<Instant>())).thenAnswer { invocation ->
            scheduledTasks.add(invocation.getArgument(0))
            mock<ScheduledFuture<*>>().also { scheduledFutures.add(it) }
        }
    }

    @Test
    fun `종료 시작 시 예약된 host-end-available을 취소한다`() {
        val startedAt = now.minusMinutes(1)
        val endingStartedAt = now.plusMinutes(1)

        scheduler.scheduleIfNeeded(partyId = 1L, startedAt = startedAt)
        scheduler.onRealtimePartyEndingStarted(
            RealtimePartyEndingStartedEvent(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endingStartedAt.plusSeconds(60),
            ),
        )
        scheduledTasks.first().run()

        verify(scheduledFutures.first()).cancel(false)
        verify(sseEmitterRegistry, never()).broadcastHost(eq(1L), anyEvent())
    }

    @Test
    fun `host-end-available은 한 번 발송된 뒤 재예약하지 않는다`() {
        val startedAt = now.minusMinutes(5)

        scheduler.scheduleIfNeeded(partyId = 1L, startedAt = startedAt)
        scheduledTasks.first().run()
        scheduler.scheduleIfNeeded(partyId = 1L, startedAt = startedAt)

        assertEquals(1, scheduledTasks.size)
        verify(sseEmitterRegistry).broadcastHost(eq(1L), anyEvent())
    }

    private fun anyEvent(): Set<ResponseBodyEmitter.DataWithMediaType> = any()
}
