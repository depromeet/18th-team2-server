package com.team2.server.burstgame.domain.candle

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandleBlowSessionTest {
    private val partyStartedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private val startedAt = partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)
    private val endsAt = startedAt.plusSeconds(CandleBlowPolicy.DURATION_SECONDS)
    private val session = CandleBlowSession.fromPartyStartedAt(partyId = 1L, partyStartedAt = partyStartedAt)

    @Test
    fun `파티 시작 시각으로 촛불 시작 종료 시각을 계산한다`() {
        assertEquals(startedAt, session.startedAt)
        assertEquals(endsAt, session.endsAt)
    }

    @Test
    fun `시작 전 snapshot은 WAITING이다`() {
        val snapshot = session.snapshot(startedAt.minusNanos(1))

        assertEquals(CandleBlowStatus.WAITING, snapshot.status)
        assertEquals(CandleBlowPolicy.CANDLE_COUNT, snapshot.remainingCount)
        assertTrue(snapshot.candles.none { it.extinguished })
    }

    @Test
    fun `촛불 끄기 성공은 촛불 상태를 갱신한다`() {
        val result = session.blow(candleId = 3, now = startedAt.plusSeconds(1))

        assertTrue(result.changed)
        assertEquals(listOf(3), extinguishedCandleIds(result.snapshot))
        assertEquals(CandleBlowPolicy.CANDLE_COUNT - 1, result.snapshot.remainingCount)
    }

    @Test
    fun `이미 꺼진 촛불은 멱등으로 현재 상태만 반환한다`() {
        session.blow(candleId = 3, now = startedAt.plusSeconds(1))

        val duplicate = session.blow(candleId = 3, now = startedAt.plusSeconds(2))

        assertFalse(duplicate.changed)
        assertEquals(listOf(3), extinguishedCandleIds(duplicate.snapshot))
    }

    @Test
    fun `candleId는 1부터 9까지만 허용한다`() {
        val tooSmall =
            assertThrows<BusinessException> {
                session.blow(candleId = 0, now = startedAt.plusSeconds(1))
            }
        val tooLarge =
            assertThrows<BusinessException> {
                session.blow(candleId = CandleBlowPolicy.CANDLE_COUNT + 1, now = startedAt.plusSeconds(1))
            }

        assertEquals(ErrorCode.INVALID_INPUT, tooSmall.errorCode)
        assertEquals(ErrorCode.INVALID_INPUT, tooLarge.errorCode)
    }

    @Test
    fun `시작 전에는 촛불을 끌 수 없다`() {
        val ex =
            assertThrows<BusinessException> {
                session.blow(candleId = 1, now = startedAt.minusNanos(1))
            }

        assertEquals(ErrorCode.CANDLE_BLOW_NOT_STARTED, ex.errorCode)
    }

    @Test
    fun `snapshot은 종료 시각이 지나면 TIMEOUT 상태로 전이한다`() {
        val snapshot = session.snapshot(endsAt)

        assertEquals(CandleBlowStatus.FINISHED, snapshot.status)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, snapshot.finishedReason)
    }

    @Test
    fun `종료 시간이 시작 시간보다 늦어야 한다`() {
        assertThrows<IllegalArgumentException> {
            CandleBlowSession(
                partyId = 1L,
                startedAt = startedAt,
                endsAt = startedAt,
            )
        }
    }

    @Test
    fun `9개가 모두 꺼지면 ALL_EXTINGUISHED로 종료한다`() {
        (1..CandleBlowPolicy.CANDLE_COUNT).forEach { candleId ->
            session.blow(candleId = candleId, now = startedAt.plusSeconds(candleId.toLong()))
        }

        val snapshot = session.snapshot(startedAt.plusSeconds(10))

        assertEquals(CandleBlowStatus.FINISHED, snapshot.status)
        assertEquals(CandleBlowFinishedReason.ALL_EXTINGUISHED, snapshot.finishedReason)
        assertEquals(0, snapshot.remainingCount)
        assertEquals((1..CandleBlowPolicy.CANDLE_COUNT).toList(), extinguishedCandleIds(snapshot))
    }

    @Test
    fun `종료 시각이 지나면 TIMEOUT으로 종료한다`() {
        val finishedNow = session.finishIfTimedOut(endsAt)
        val snapshot = session.snapshot(endsAt)

        assertTrue(finishedNow)
        assertEquals(CandleBlowStatus.FINISHED, snapshot.status)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, snapshot.finishedReason)
    }

    @Test
    fun `종료 후 촛불 끄기는 현재 종료 상태만 반환한다`() {
        session.finishIfTimedOut(endsAt)

        val result = session.blow(candleId = 1, now = endsAt.plusSeconds(1))

        assertFalse(result.changed)
        assertEquals(CandleBlowFinishedReason.TIMEOUT, result.snapshot.finishedReason)
    }

    private fun extinguishedCandleIds(snapshot: CandleBlowSnapshot): List<Int> =
        snapshot.candles
            .filter { it.extinguished }
            .map { it.candleId }
}
