package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryCandleBlowSessionStoreTest {
    private val partyStartedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private val store = InMemoryCandleBlowSessionStore()

    @Test
    fun `파티별 세션을 생성하고 조회한다`() {
        val created =
            store.getOrCreateWithLock(1L, { session(1L) }) { _, created ->
                created
            }
        val found =
            store.withSessionLock(1L) { session ->
                session.partyId
            }

        assertTrue(created)
        assertEquals(1L, found)
    }

    @Test
    fun `이미 세션이 있으면 기존 세션을 반환한다`() {
        val first =
            store.getOrCreateWithLock(1L, { session(1L) }) { session, created ->
                session.partyId to created
            }

        val second =
            store.getOrCreateWithLock(1L, { session(1L) }) { session, created ->
                session.partyId to created
            }

        assertEquals(1L to true, first)
        assertEquals(1L to false, second)
    }

    @Test
    fun `동시에 생성 요청이 들어와도 세션은 하나만 생성한다`() {
        val requestCount = 20
        val executor = Executors.newFixedThreadPool(requestCount)
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)

        try {
            val futures =
                (1..requestCount).map {
                    executor.submit<Boolean> {
                        ready.countDown()
                        start.await()
                        store.getOrCreateWithLock(1L, { session(1L) }) { _, created ->
                            created
                        }
                    }
                }

            assertTrue(ready.await(1, TimeUnit.SECONDS))
            start.countDown()

            val createdCount = futures.count { it.get(1, TimeUnit.SECONDS) }
            assertEquals(1, createdCount)
        } finally {
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `partyId가 다른 세션은 따로 보관한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }
        store.getOrCreateWithLock(2L, { session(2L) }) { _, _ -> }

        assertEquals(1L, store.withSessionLock(1L) { it.partyId })
        assertEquals(2L, store.withSessionLock(2L) { it.partyId })
    }

    @Test
    fun `partyId가 다른 세션을 생성하면 실패한다`() {
        val ex =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                store.getOrCreateWithLock(1L, { session(2L) }) { _, _ -> }
            }

        assertTrue(ex.message.orEmpty().contains("partyId mismatch"))
    }

    @Test
    fun `동시에 촛불을 꺼도 party lock 안에서 일관되게 mutation한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }
        val requestCount = 9
        val executor = Executors.newFixedThreadPool(requestCount)
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)

        try {
            val futures =
                (1..requestCount).map { candleId ->
                    executor.submit<Boolean> {
                        ready.countDown()
                        start.await()
                        store.withSessionLock(1L) { session ->
                            session
                                .blow(
                                    candleId = candleId,
                                    now = partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS),
                                ).changed
                        } == true
                    }
                }

            assertTrue(ready.await(1, TimeUnit.SECONDS))
            start.countDown()

            assertTrue(futures.all { it.get(1, TimeUnit.SECONDS) })
            val remainingCount =
                store.withSessionLock(1L) {
                    it.snapshot(partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)).remainingCount
                }
            assertEquals(0, remainingCount)
        } finally {
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `종료 시각과 TTL이 지난 세션을 제거한다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }

        store.removeExpired(
            partyStartedAt
                .plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + CandleBlowPolicy.DURATION_SECONDS)
                .plus(CandleBlowPolicy.SESSION_TTL),
        )

        assertEquals(null, store.withSessionLock(1L) { it.partyId })
    }

    @Test
    fun `TTL이 지나지 않은 세션은 제거하지 않는다`() {
        store.getOrCreateWithLock(1L, { session(1L) }) { _, _ -> }

        store.removeExpired(
            partyStartedAt
                .plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + CandleBlowPolicy.DURATION_SECONDS)
                .plus(CandleBlowPolicy.SESSION_TTL)
                .minusNanos(1),
        )

        assertEquals(1L, store.withSessionLock(1L) { it.partyId })
    }

    private fun session(partyId: Long): CandleBlowSession =
        CandleBlowSession.fromPartyStartedAt(
            partyId = partyId,
            partyStartedAt = partyStartedAt,
        )
}
