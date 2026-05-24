package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.infrastructure.memory.InMemoryBurstGameSessionStore
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BurstGameSessionServiceTest {
    private val startedAt = LocalDateTime.of(2026, 5, 14, 20, 10)
    private val candleBlowStatusReader = FakeCandleBlowStatusReader()
    private val sessionService =
        BurstGameSessionService(
            sessionStore = InMemoryBurstGameSessionStore(),
            candleBlowStatusReader = candleBlowStatusReader,
        )

    @Test
    fun `동시에 submit이 들어와도 tap count와 stateVersion을 일관되게 반영한다`() {
        val submitCount = 20
        val executor = Executors.newFixedThreadPool(submitCount)
        val ready = CountDownLatch(submitCount)
        val start = CountDownLatch(1)
        sessionService.start(partyId = 1L, participant = participant(0), now = startedAt)

        try {
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
        } finally {
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }

        val snapshot = sessionService.snapshot(1L, participant(1), startedAt.plusSeconds(2)).snapshot

        assertEquals(submitCount, snapshot.totalTapCount)
        assertEquals(submitCount.toLong(), snapshot.stateVersion)
    }

    @Test
    fun `촛불끄기가 끝나지 않았으면 start를 거부한다`() {
        candleBlowStatusReader.finished = false

        val ex =
            assertThrows<BusinessException> {
                sessionService.start(partyId = 1L, participant = participant(1), now = startedAt)
            }

        assertEquals(ErrorCode.BURST_GAME_NOT_READY, ex.errorCode)
    }

    @Test
    fun `촛불끄기가 끝났으면 start를 허용한다`() {
        val result = sessionService.start(partyId = 1L, participant = participant(1), now = startedAt)

        assertTrue(result is BurstGameSessionService.StartResult.Started)
        assertTrue(result.created)
        assertEquals(BurstGameRoundStatus.ACTIVE, result.snapshot.status)
    }

    @Test
    fun `active session start 재호출은 촛불끄기 상태를 다시 검증하지 않는다`() {
        sessionService.start(partyId = 1L, participant = participant(1), now = startedAt)
        candleBlowStatusReader.finished = false

        val result = sessionService.start(partyId = 1L, participant = participant(1), now = startedAt.plusSeconds(1))

        assertTrue(result is BurstGameSessionService.StartResult.Started)
        assertFalse(result.created)
    }

    @Test
    fun `종료 시간이 지난 active session start 재호출은 종료 상태와 endedNow를 반환한다`() {
        sessionService.start(partyId = 1L, participant = participant(1), now = startedAt)

        val result = sessionService.start(partyId = 1L, participant = participant(1), now = startedAt.plusSeconds(20))

        assertTrue(result is BurstGameSessionService.StartResult.AlreadyEnded)
        assertTrue(result.endedNow)
        assertEquals(BurstGameRoundStatus.ENDED, result.snapshot.status)
    }

    private fun participant(participantId: Long): BurstGameParticipantInfo =
        BurstGameParticipantInfo(
            participantId = participantId,
            nickname = "p$participantId",
            characterId = null,
            characterImageUrl = null,
            role = "PARTICIPANT",
        )

    private class FakeCandleBlowStatusReader : CandleBlowStatusReader {
        var finished = true

        override fun isCandleBlowFinished(
            partyId: Long,
            now: LocalDateTime,
        ): Boolean = finished
    }
}
