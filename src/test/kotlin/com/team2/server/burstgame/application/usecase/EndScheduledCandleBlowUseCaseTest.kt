package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowSession
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

class EndScheduledCandleBlowUseCaseTest {
    private val startedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private val durationSeconds = 300L
    private lateinit var sessionStore: InMemoryCandleBlowSessionStore
    private lateinit var eventBroadcaster: CandleBlowEventBroadcaster
    private lateinit var useCase: EndScheduledCandleBlowUseCase

    @BeforeTest
    fun setUp() {
        sessionStore = InMemoryCandleBlowSessionStore()
        eventBroadcaster = mock()
        useCase =
            EndScheduledCandleBlowUseCase(
                sessionStore = sessionStore,
                eventBroadcaster = eventBroadcaster,
            )
    }

    @Test
    fun `세션이 없으면 null을 반환한다`() {
        assertEquals(null, useCase(partyId = 1L, now = candleEndedAt()))
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    @Test
    fun `종료 시간이 지나면 TIMEOUT으로 종료하고 ended 이벤트를 발행한다`() {
        createSession()

        val result = useCase(partyId = 1L, now = candleEndedAt())

        assertEquals(CandleBlowStatus.FINISHED, result?.status)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, result?.finishedReason)
        verify(eventBroadcaster).broadcastEnded(any())
    }

    @Test
    fun `이미 종료된 세션이면 ended 이벤트를 다시 발행하지 않는다`() {
        createSession { session ->
            (1..CandleBlowPolicy.CANDLE_COUNT).forEach { candleId ->
                session.blow(candleId, candleStartedAt().plusSeconds(candleId.toLong()))
            }
        }

        val result = useCase(partyId = 1L, now = candleEndedAt())

        assertEquals(CandleBlowFinishedReason.ALL_EXTINGUISHED, result?.finishedReason)
        verify(eventBroadcaster, never()).broadcastEnded(any())
    }

    private fun createSession(onCreated: (CandleBlowSession) -> Unit = {}) {
        sessionStore.getOrCreateWithLock(
            partyId = 1L,
            sessionFactory = {
                CandleBlowSession.fromStartedAt(
                    partyId = 1L,
                    startedAt = startedAt,
                    durationSeconds = durationSeconds,
                )
            },
        ) { session, _ ->
            onCreated(session)
        }
    }

    private fun candleStartedAt(): LocalDateTime = startedAt

    private fun candleEndedAt(): LocalDateTime = candleStartedAt().plusSeconds(durationSeconds)
}
