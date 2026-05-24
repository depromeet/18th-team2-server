package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InMemoryCandleBlowSessionStoreTest {
    private val partyStartedAt = LocalDateTime.of(2026, 5, 24, 20, 0)
    private val store = InMemoryCandleBlowSessionStore()

    @Test
    fun `파티별 세션을 생성하고 조회한다`() {
        val result = store.getOrCreate(1L) { session(1L) }

        assertTrue(result is CandleBlowSessionStore.CreateResult.Created)
        assertSame(result.session, store.findByPartyId(1L))
    }

    @Test
    fun `이미 세션이 있으면 기존 세션을 반환한다`() {
        val created = store.getOrCreate(1L) { session(1L) } as CandleBlowSessionStore.CreateResult.Created

        val existing = store.getOrCreate(1L) { session(1L) }

        assertTrue(existing is CandleBlowSessionStore.CreateResult.Existing)
        assertSame(created.session, existing.session)
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
                    executor.submit<CandleBlowSession> {
                        ready.countDown()
                        start.await()
                        when (val result = store.getOrCreate(1L) { session(1L) }) {
                            is CandleBlowSessionStore.CreateResult.Created -> result.session
                            is CandleBlowSessionStore.CreateResult.Existing -> result.session
                        }
                    }
                }

            assertTrue(ready.await(1, TimeUnit.SECONDS))
            start.countDown()

            val sessions = futures.map { it.get(1, TimeUnit.SECONDS) }.toSet()
            assertEquals(1, sessions.size)
        } finally {
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `partyId가 다른 세션은 따로 보관한다`() {
        val first = store.getOrCreate(1L) { session(1L) } as CandleBlowSessionStore.CreateResult.Created
        val second = store.getOrCreate(2L) { session(2L) } as CandleBlowSessionStore.CreateResult.Created

        assertSame(first.session, store.findByPartyId(1L))
        assertSame(second.session, store.findByPartyId(2L))
    }

    @Test
    fun `partyId가 다른 세션을 생성하면 실패한다`() {
        val ex =
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                store.getOrCreate(1L) { session(2L) }
            }

        assertTrue(ex.message.orEmpty().contains("partyId mismatch"))
    }

    private fun session(partyId: Long): CandleBlowSession =
        CandleBlowSession.fromPartyStartedAt(
            partyId = partyId,
            partyStartedAt = partyStartedAt,
        )
}
