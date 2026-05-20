package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.infrastructure.memory.InMemoryBurstGameSessionStore
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BurstGameSessionServiceTest {
    private val startedAt = LocalDateTime.of(2026, 5, 14, 20, 10)
    private val sessionService =
        BurstGameSessionService(
            sessionStore = InMemoryBurstGameSessionStore(),
            candleBlowStatusReader = AlwaysReadyCandleBlowStatusReader,
        )

    @Test
    fun `동시에 submit이 들어와도 tap count와 stateVersion을 일관되게 반영한다`() {
        val submitCount = 20
        val executor = Executors.newFixedThreadPool(submitCount)
        val ready = CountDownLatch(submitCount)
        val start = CountDownLatch(1)
        sessionService.start(partyId = 1L, participant = participant(0), now = startedAt)

        val futures =
            (1..submitCount).map { participantId ->
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    sessionService
                        .submit(
                            partyId = 1L,
                            participant = participant(participantId.toLong()),
                            tapCount = 1,
                            clientSequence = 1,
                            now = startedAt.plusSeconds(1),
                        ).accepted
                }
            }

        assertTrue(ready.await(1, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(futures.all { it.get(1, TimeUnit.SECONDS) })
        executor.shutdown()
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))

        val snapshot = sessionService.snapshot(1L, participant(1), startedAt.plusSeconds(2)).snapshot

        assertEquals(submitCount, snapshot.totalTapCount)
        assertEquals(submitCount.toLong(), snapshot.stateVersion)
    }

    private fun participant(participantId: Long): BurstGameParticipantInfo =
        BurstGameParticipantInfo(
            participantId = participantId,
            nickname = "p$participantId",
            characterId = null,
            characterImageUrl = null,
            role = "PARTICIPANT",
        )

    private object AlwaysReadyCandleBlowStatusReader : CandleBlowStatusReader {
        override fun isCandleBlowCompleted(partyId: Long): Boolean = true
    }
}
