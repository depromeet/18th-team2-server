package com.team2.server.party.infrastructure.sse

import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndRecoveryResult
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.event.RealtimePartyEndingStartedEvent
import com.team2.server.party.application.event.RealtimePartyStartedEvent
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.usecase.RecoverRealtimePartyEndScheduleUseCase
import com.team2.server.party.application.usecase.StartAutomaticRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ScheduledFuture
import kotlin.test.assertEquals

class PartyEndSchedulerTest {
    private val taskScheduler: TaskScheduler = mock()
    private val realtimePartyEventBroadcaster: RealtimePartyEventBroadcaster = mock()
    private val recoverRealtimePartyEndScheduleUseCase: RecoverRealtimePartyEndScheduleUseCase = mock()
    private val startAutomaticRealtimePartyEndUseCase: StartAutomaticRealtimePartyEndUseCase = mock()
    private val phaseStore: PartyPhaseStore = mock()
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
                realtimePartyEventBroadcasters = listOf(realtimePartyEventBroadcaster),
                recoverRealtimePartyEndScheduleUseCase = recoverRealtimePartyEndScheduleUseCase,
                startAutomaticRealtimePartyEndUseCase = startAutomaticRealtimePartyEndUseCase,
                clock = clock,
                phaseStore = phaseStore,
            )
        whenever(taskScheduler.schedule(any<Runnable>(), any<Instant>())).thenAnswer { invocation ->
            scheduledTasks.add(invocation.getArgument(0))
            mock<ScheduledFuture<*>>().also { scheduledFutures.add(it) }
        }
    }

    @Test
    fun `등록된 모든 브로드캐스터에 종료 이벤트를 전달한다`() {
        val secondBroadcaster: RealtimePartyEventBroadcaster = mock()
        val schedulerWithTwoBroadcasters =
            PartyEndScheduler(
                taskScheduler = taskScheduler,
                realtimePartyEventBroadcasters = listOf(realtimePartyEventBroadcaster, secondBroadcaster),
                recoverRealtimePartyEndScheduleUseCase = recoverRealtimePartyEndScheduleUseCase,
                startAutomaticRealtimePartyEndUseCase = startAutomaticRealtimePartyEndUseCase,
                clock = clock,
                phaseStore = phaseStore,
            )
        val startedAt = now.minusMinutes(10)
        val endingStartedAt = startedAt.plusMinutes(30)
        val target =
            RealtimeEndingScheduleTarget(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endingStartedAt.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                startedNow = true,
            )
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(target)

        schedulerWithTwoBroadcasters.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduledTasks[0].run()
        scheduledTasks[1].run()
        scheduledTasks[2].run()

        verify(realtimePartyEventBroadcaster).broadcastPartyEnded(1L, endingStartedAt.plusSeconds(60), "주최자", now)
        verify(secondBroadcaster).broadcastPartyEnded(1L, endingStartedAt.plusSeconds(60), "주최자", now)
        verify(realtimePartyEventBroadcaster).completeParty(1L)
        verify(secondBroadcaster).completeParty(1L)
    }

    @Test
    fun `created event schedules automatic ending and sends ending events`() {
        val startedAt = now.minusMinutes(10)
        val endingStartedAt = startedAt.plusMinutes(30)
        val target =
            RealtimeEndingScheduleTarget(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endingStartedAt.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                startedNow = true,
            )
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(target)

        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduledTasks[0].run()
        scheduledTasks[1].run()
        scheduledTasks[2].run()

        verify(realtimePartyEventBroadcaster).broadcastPartyEnding(
            partyId = eq(1L),
            endingStartedAt = eq(endingStartedAt),
            endedAt = eq(endingStartedAt.plusSeconds(60)),
            endingReason = eq(RealtimePartyEndingReason.TIME_LIMIT_REACHED),
            hostNickname = eq("주최자"),
            serverNow = eq(now),
        )
        verify(realtimePartyEventBroadcaster).broadcastPartyEnded(1L, endingStartedAt.plusSeconds(60), "주최자", now)
        verify(realtimePartyEventBroadcaster).completeParty(1L)
    }

    @Test
    fun `automatic ending returning null does not schedule ending events`() {
        val startedAt = now.minusMinutes(10)
        val endingStartedAt = startedAt.plusMinutes(30)
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(null)

        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduledTasks[0].run()

        assertEquals(1, scheduledTasks.size)
        verify(realtimePartyEventBroadcaster, never())
            .broadcastPartyEnding(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `started event reschedules automatic ending to ten minutes after live start`() {
        val startedAt = now.minusMinutes(10)
        val liveStartedAt = now.minusMinutes(2)
        val endingStartedAt = liveStartedAt.plusMinutes(10)
        val target =
            RealtimeEndingScheduleTarget(
                partyId = 1L,
                endingStartedAt = endingStartedAt,
                endedAt = endingStartedAt.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                hostNickname = "주최자",
                startedNow = true,
            )
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(target)

        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduler.onRealtimePartyStarted(RealtimePartyStartedEvent(1L, liveStartedAt))
        scheduledTasks[1].run()

        verify(scheduledFutures[0]).cancel(false)
        verify(realtimePartyEventBroadcaster).broadcastPartyEnding(
            partyId = eq(1L),
            endingStartedAt = eq(endingStartedAt),
            endedAt = eq(endingStartedAt.plusSeconds(60)),
            endingReason = eq(RealtimePartyEndingReason.TIME_LIMIT_REACHED),
            hostNickname = eq("주최자"),
            serverNow = eq(now),
        )
    }

    @Test
    fun `recovery schedules automatic and ending targets without immediate ending broadcast`() {
        val endingStartedAt = now.plusMinutes(1)
        whenever(recoverRealtimePartyEndScheduleUseCase()).thenReturn(
            RealtimePartyEndRecoveryResult(
                automaticEndSchedules = listOf(RealtimeAutomaticEndSchedule(1L, endingStartedAt)),
                endingTargets =
                    listOf(
                        target(
                            partyId = 2L,
                            endingStartedAt = now.minusSeconds(10),
                            endedAt = now.plusSeconds(50),
                        ),
                    ),
            ),
        )

        scheduler.recoverSchedules()

        assertEquals(2, scheduledTasks.size)
        verify(realtimePartyEventBroadcaster, never())
            .broadcastPartyEnding(eq(2L), any(), any(), any(), any(), any())
    }

    @Test
    fun `late party-ending is skipped when party-ended was already sent`() {
        val target = target(1L, now.minusSeconds(70), now.minusSeconds(10))
        whenever(recoverRealtimePartyEndScheduleUseCase()).thenReturn(
            RealtimePartyEndRecoveryResult(
                automaticEndSchedules = emptyList(),
                endingTargets = listOf(target),
            ),
        )

        scheduler.recoverSchedules()
        scheduledTasks[0].run()
        scheduler.onRealtimePartyEndingStarted(
            RealtimePartyEndingStartedEvent(
                partyId = 1L,
                endingStartedAt = target.endingStartedAt,
                endedAt = target.endedAt,
                endingReason = target.endingReason,
                hostNickname = target.hostNickname,
            ),
        )

        verify(realtimePartyEventBroadcaster, times(1)).broadcastPartyEnded(eq(1L), any(), eq("주최자"), any())
    }

    @Test
    fun `종료 카운트다운 이벤트에 서버 기준 시각을 함께 실어 보낸다`() {
        val endingStartedAt = now.minusSeconds(5)
        val target = target(1L, endingStartedAt, endingStartedAt.plusSeconds(60))

        scheduler.onRealtimePartyEndingStarted(
            RealtimePartyEndingStartedEvent(
                partyId = target.partyId,
                endingStartedAt = target.endingStartedAt,
                endedAt = target.endedAt,
                endingReason = target.endingReason,
                hostNickname = target.hostNickname,
            ),
        )
        scheduledTasks[0].run()

        verify(realtimePartyEventBroadcaster).broadcastPartyEnding(
            partyId = eq(1L),
            endingStartedAt = eq(endingStartedAt),
            endedAt = eq(endingStartedAt.plusSeconds(60)),
            endingReason = eq(RealtimePartyEndingReason.HOST_REQUEST),
            hostNickname = eq("주최자"),
            serverNow = eq(now),
        )
        verify(realtimePartyEventBroadcaster)
            .broadcastPartyEnded(1L, endingStartedAt.plusSeconds(60), "주최자", now)
    }

    @Test
    fun `cancelSchedules cancels stored tasks`() {
        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, now.minusMinutes(10)))
        scheduler.onRealtimePartyEndingStarted(
            RealtimePartyEndingStartedEvent(
                partyId = 1L,
                endingStartedAt = now,
                endedAt = now.plusSeconds(60),
                endingReason = RealtimePartyEndingReason.HOST_REQUEST,
                hostNickname = "주최자",
            ),
        )

        scheduler.cancelSchedules()

        scheduledFutures.forEach { verify(it).cancel(false) }
    }

    private fun target(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endedAt: LocalDateTime,
    ): RealtimeEndingScheduleTarget =
        RealtimeEndingScheduleTarget(
            partyId = partyId,
            endingStartedAt = endingStartedAt,
            endedAt = endedAt,
            endingReason = RealtimePartyEndingReason.HOST_REQUEST,
            hostNickname = "주최자",
        )
}
