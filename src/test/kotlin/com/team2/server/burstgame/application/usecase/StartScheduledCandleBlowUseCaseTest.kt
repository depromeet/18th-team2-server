package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.burstgame.infrastructure.candle.InMemoryCandleBlowSessionStore
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartScheduledCandleBlowUseCaseTest {
    private val partyStartedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private lateinit var sessionStore: InMemoryCandleBlowSessionStore
    private lateinit var eventBroadcaster: CandleBlowEventBroadcaster
    private lateinit var useCase: StartScheduledCandleBlowUseCase

    @BeforeTest
    fun setUp() {
        sessionStore = InMemoryCandleBlowSessionStore()
        eventBroadcaster = mock()
        useCase =
            StartScheduledCandleBlowUseCase(
                sessionStore = sessionStore,
                eventBroadcaster = eventBroadcaster,
            )
    }

    @Test
    fun `시작 시간이 되면 세션을 생성하고 started 이벤트를 발행한다`() {
        val snapshot =
            useCase(
                partyId = 1L,
                partyStartedAt = partyStartedAt,
                now = candleStartedAt(),
            )

        assertEquals(CandleBlowStatus.ACTIVE, snapshot.status)
        verify(eventBroadcaster).broadcastStarted(snapshot)
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    @Test
    fun `시작 실행 시 이미 종료 시간이 지났으면 ended 이벤트를 발행한다`() {
        val snapshot =
            useCase(
                partyId = 1L,
                partyStartedAt = partyStartedAt,
                now = candleEndedAt(),
            )

        assertEquals(CandleBlowStatus.FINISHED, snapshot.status)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, snapshot.finishedReason)
        verify(eventBroadcaster).broadcastEnded(snapshot)
        verify(eventBroadcaster, never()).broadcastStarted(any())
    }

    @Test
    fun `시작 시간 전이면 이벤트를 발행하지 않는다`() {
        val snapshot =
            useCase(
                partyId = 1L,
                partyStartedAt = partyStartedAt,
                now = candleStartedAt().minusNanos(1),
            )

        assertEquals(CandleBlowStatus.WAITING, snapshot.status)
        verify(eventBroadcaster, never()).broadcastStarted(any())
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    private fun candleStartedAt(): LocalDateTime = partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)

    private fun candleEndedAt(): LocalDateTime = candleStartedAt().plusSeconds(CandleBlowPolicy.DURATION_SECONDS)
}
