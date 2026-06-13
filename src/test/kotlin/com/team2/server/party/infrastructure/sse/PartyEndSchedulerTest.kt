package com.team2.server.party.infrastructure.sse

import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndRecoveryResult
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.event.RealtimePartyEndingStartedEvent
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
                realtimePartyEventBroadcaster = realtimePartyEventBroadcaster,
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
    fun `created event schedules automatic ending and sends ending events`() {
        val startedAt = now.minusMinutes(10)
        val endingStartedAt = startedAt.plusMinutes(10)
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
        )
        verify(realtimePartyEventBroadcaster).broadcastPartyEnded(1L, endingStartedAt.plusSeconds(60), "주최자")
        verify(realtimePartyEventBroadcaster).completeParty(1L)
    }

    @Test
    fun `automatic ending returning null does not schedule ending events`() {
        val startedAt = now.minusMinutes(10)
        val endingStartedAt = startedAt.plusMinutes(10)
        whenever(startAutomaticRealtimePartyEndUseCase(1L, endingStartedAt)).thenReturn(null)

        scheduler.onRealtimePartyCreated(RealtimePartyCreatedEvent(1L, startedAt))
        scheduledTasks[0].run()

        assertEquals(1, scheduledTasks.size)
        verify(realtimePartyEventBroadcaster, never()).broadcastPartyEnding(any(), any(), any(), any(), any())
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
        verify(realtimePartyEventBroadcaster, never()).broadcastPartyEnding(eq(2L), any(), any(), any(), any())
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

        verify(realtimePartyEventBroadcaster, times(1)).broadcastPartyEnded(eq(1L), any(), eq("주최자"))
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
