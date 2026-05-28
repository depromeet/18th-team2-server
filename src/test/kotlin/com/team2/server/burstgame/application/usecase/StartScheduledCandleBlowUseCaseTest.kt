package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.burstgame.infrastructure.candle.InMemoryCandleBlowSessionStore
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartScheduledCandleBlowUseCaseTest {
    private val hostEnteredAt = LocalDateTime.of(2026, 5, 24, 20, 0)
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
                candleBlowProperties = CandleBlowProperties(),
            )
    }

    @Test
    fun `시작 시간이 되면 세션을 생성하고 started 이벤트를 발행한다`() {
        val result =
            useCase(
                partyId = 1L,
                hostEnteredAt = hostEnteredAt,
                now = candleStartedAt(),
            )

        assertEquals(CandleBlowStatus.ACTIVE, result.status)
        verify(eventBroadcaster).broadcastStarted(any())
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    @Test
    fun `시작 실행 시 이미 종료 시간이 지났으면 ended 이벤트를 발행한다`() {
        val result =
            useCase(
                partyId = 1L,
                hostEnteredAt = hostEnteredAt,
                now = candleEndedAt(),
            )

        assertEquals(CandleBlowStatus.FINISHED, result.status)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, result.finishedReason)
        verify(eventBroadcaster).broadcastEnded(any())
        verify(eventBroadcaster, never()).broadcastStarted(any())
    }

    @Test
    fun `같은 세션의 started 이벤트는 한 번만 발행한다`() {
        useCase(
            partyId = 1L,
            hostEnteredAt = hostEnteredAt,
            now = candleStartedAt(),
        )
        useCase(
            partyId = 1L,
            hostEnteredAt = hostEnteredAt,
            now = candleStartedAt().plusSeconds(1),
        )

        verify(eventBroadcaster, times(1)).broadcastStarted(any())
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    @Test
    fun `시작 시간 전이면 이벤트를 발행하지 않는다`() {
        val result =
            useCase(
                partyId = 1L,
                hostEnteredAt = hostEnteredAt,
                now = candleStartedAt().minusNanos(1),
            )

        assertEquals(CandleBlowStatus.WAITING, result.status)
        verify(eventBroadcaster, never()).broadcastStarted(any())
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    private fun candleStartedAt(): LocalDateTime = hostEnteredAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)

    private fun candleEndedAt(): LocalDateTime = candleStartedAt().plusSeconds(CandleBlowPolicy.DURATION_SECONDS)
}
