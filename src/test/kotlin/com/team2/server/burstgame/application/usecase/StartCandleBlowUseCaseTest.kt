package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.burstgame.infrastructure.candle.InMemoryCandleBlowSessionStore
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartCandleBlowUseCaseTest {
    private val startedAt = LocalDateTime.of(2026, 6, 8, 20, 17, 34)
    private lateinit var sessionStore: InMemoryCandleBlowSessionStore
    private lateinit var eventBroadcaster: CandleBlowEventBroadcaster
    private lateinit var useCase: StartCandleBlowUseCase

    @BeforeTest
    fun setUp() {
        sessionStore = InMemoryCandleBlowSessionStore()
        eventBroadcaster = mock()
        useCase =
            StartCandleBlowUseCase(
                sessionStore = sessionStore,
                eventBroadcaster = eventBroadcaster,
                candleBlowProperties = CandleBlowProperties(),
            )
    }

    @Test
    fun `CANDLE phase 시작 시각부터 촛불끄기 세션을 ACTIVE로 시작한다`() {
        val result = useCase(partyId = 1L, startedAt = startedAt)

        assertEquals(CandleBlowStatus.ACTIVE, result.status)
        verify(eventBroadcaster).broadcastStarted(any())
    }

    @Test
    fun `이미 시작된 세션이면 started 이벤트를 다시 보내지 않는다`() {
        useCase(partyId = 1L, startedAt = startedAt)
        useCase(partyId = 1L, startedAt = startedAt.plusSeconds(1))

        verify(eventBroadcaster, times(1)).broadcastStarted(any())
    }
}
