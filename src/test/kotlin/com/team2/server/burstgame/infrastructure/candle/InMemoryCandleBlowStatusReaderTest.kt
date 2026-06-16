package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryCandleBlowStatusReaderTest {
    private val startedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private val candleBlowProperties = CandleBlowProperties()
    private val store = InMemoryCandleBlowSessionStore()
    private val reader = InMemoryCandleBlowStatusReader(store)

    @Test
    fun `세션이 없으면 촛불끄기 미완료로 판단한다`() {
        assertFalse(reader.isCandleBlowFinished(partyId = 1L, now = startedAt.plusDays(1)))
    }

    @Test
    fun `촛불끄기가 active이면 미완료로 판단한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }

        assertFalse(reader.isCandleBlowFinished(partyId = 1L, now = startedAt))
    }

    @Test
    fun `모든 촛불이 꺼졌으면 완료로 판단한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { session, _ ->
            (1..CandleBlowPolicy.CANDLE_COUNT).forEach { candleId ->
                session.blow(candleId = candleId, now = startedAt.plusSeconds(candleId.toLong()))
            }
        }

        assertTrue(reader.isCandleBlowFinished(partyId = 1L, now = startedAt.plusSeconds(10)))
    }

    @Test
    fun `종료 시각이 지나면 timeout 전이 후 완료로 판단한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }

        assertTrue(
            reader.isCandleBlowFinished(
                partyId = 1L,
                now = startedAt.plusSeconds(candleBlowProperties.durationSeconds),
            ),
        )
        assertTrue(
            store.withSessionLock(1L) { session ->
                session.finishedReason == CandleBlowFinishedReason.TIMEOUT
            } == true,
        )
    }

    private fun session(partyId: Long): CandleBlowSession =
        CandleBlowSession.fromStartedAt(
            partyId = partyId,
            startedAt = startedAt,
            durationSeconds = candleBlowProperties.durationSeconds,
        )
}
