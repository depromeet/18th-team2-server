package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.event.BurstGameStartedEvent
import com.team2.server.burstgame.domain.BurstGameRankingEntry
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.chat.application.port.PartySseEventPublisher
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SseBurstGameEventBroadcasterTest {
    private val partySseEventPublisher: PartySseEventPublisher = mock()
    private val applicationEventPublisher: ApplicationEventPublisher = mock()
    private val broadcaster = SseBurstGameEventBroadcaster(partySseEventPublisher, applicationEventPublisher)

    @AfterTest
    fun tearDown() {
        broadcaster.shutdown()
    }

    @Test
    fun `started 이벤트는 미래 startedAt을 전송하고 파티 단계 전환에는 발행 시각을 사용한다`() {
        val startedAt = LocalDateTime.of(2026, 5, 18, 14, 20, 5)
        val serverTime = startedAt.minusSeconds(5)
        val snapshot = snapshot(startedAt = startedAt).copy(serverTime = serverTime)

        broadcaster.broadcastStarted(snapshot)

        val eventCaptor = argumentCaptor<Set<ResponseBodyEmitter.DataWithMediaType>>()
        verify(partySseEventPublisher).broadcastAfterCommit(eq(1L), eventCaptor.capture(), isNull())
        val payload =
            eventCaptor.firstValue
                .map { it.data }
                .filterIsInstance<SseBurstGameEventBroadcaster.BurstGameStartedPayload>()
                .single()
        assertEquals(startedAt, payload.startedAt)
        assertEquals(serverTime, payload.serverTime)
        verify(applicationEventPublisher).publishEvent(BurstGameStartedEvent(1L, serverTime))
    }

    @Test
    fun `progress 이벤트는 throttle 구간에서 최신 값만 전송한다`() {
        broadcaster.broadcastProgress(snapshot(totalTapCount = 1, stateVersion = 1))
        val latest = snapshot(totalTapCount = 2, stateVersion = 2)
        broadcaster.broadcastProgress(latest)

        val eventCaptor = argumentCaptor<Set<ResponseBodyEmitter.DataWithMediaType>>()
        verify(
            partySseEventPublisher,
            timeout(1_000).times(1),
        ).broadcastAfterCommit(
            eq(1L),
            eventCaptor.capture(),
            isNull(),
        )

        val payload =
            eventCaptor.firstValue
                .map { it.data }
                .filterIsInstance<SseBurstGameEventBroadcaster.BurstGameProgressPayload>()
                .single()
        assertEquals(latest.endsAt, payload.endsAt)
        assertEquals(latest.serverTime, payload.serverTime)
    }

    @Test
    fun `ended 이후 pending progress는 전송하지 않는다`() {
        broadcaster.broadcastProgress(snapshot(totalTapCount = 1, stateVersion = 1))
        val endedSnapshot = snapshot(status = BurstGameRoundStatus.ENDED, totalTapCount = 1, stateVersion = 2)
        broadcaster.broadcastEnded(endedSnapshot)

        verify(partySseEventPublisher, timeout(400).times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `같은 party의 새 session progress는 이전 ended cleanup 이후에도 전송한다`() {
        val endedStartedAt = LocalDateTime.of(2026, 5, 18, 14, 20)
        val newStartedAt = endedStartedAt.plusMinutes(1)

        broadcaster.broadcastEnded(
            snapshot(
                status = BurstGameRoundStatus.ENDED,
                totalTapCount = 1,
                stateVersion = 2,
                startedAt = endedStartedAt,
            ),
        )
        broadcaster.broadcastProgress(snapshot(totalTapCount = 2, stateVersion = 1, startedAt = newStartedAt))

        verify(partySseEventPublisher, timeout(1_000).times(2)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    private fun anyEvent(): Set<ResponseBodyEmitter.DataWithMediaType> = any()

    private fun snapshot(
        status: BurstGameRoundStatus = BurstGameRoundStatus.ACTIVE,
        totalTapCount: Int = 0,
        stateVersion: Long = 0,
        startedAt: LocalDateTime = LocalDateTime.of(2026, 5, 18, 14, 20),
    ): BurstGameSnapshot =
        BurstGameSnapshot(
            partyId = 1L,
            myParticipantId = 10L,
            status = status,
            startedAt = startedAt,
            endsAt = startedAt.plusSeconds(20),
            totalTapCount = totalTapCount,
            myTapCount = totalTapCount,
            colorChanged = false,
            stateVersion = stateVersion,
            serverTime = startedAt,
            remainingSeconds = 20,
            rankings =
                listOf(
                    BurstGameRankingEntry(
                        rank = 1,
                        participantId = 10L,
                        nickname = "player",
                        characterId = null,
                        characterImageUrl = null,
                        role = "PARTICIPANT",
                        tapCount = totalTapCount,
                    ),
                ),
        )
}
