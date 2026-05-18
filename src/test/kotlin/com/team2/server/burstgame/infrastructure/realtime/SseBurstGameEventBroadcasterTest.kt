package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.domain.BurstGameRankingEntry
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.burstgame.domain.BurstGameWinner
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test

class SseBurstGameEventBroadcasterTest {
    private val chatSseGateway: ChatSseGateway = mock()
    private val broadcaster = SseBurstGameEventBroadcaster(chatSseGateway)

    @AfterTest
    fun tearDown() {
        broadcaster.shutdown()
    }

    @Test
    fun `started 이벤트는 즉시 전송한다`() {
        broadcaster.broadcastStarted(snapshot())

        verify(chatSseGateway).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `progress 이벤트는 throttle 구간에서 최신 값만 전송한다`() {
        broadcaster.broadcastProgress(snapshot(totalTapCount = 1, stateVersion = 1))
        broadcaster.broadcastProgress(snapshot(totalTapCount = 2, stateVersion = 2))

        verify(chatSseGateway, timeout(1_000).times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `ended 이후 pending progress는 전송하지 않는다`() {
        broadcaster.broadcastProgress(snapshot(totalTapCount = 1, stateVersion = 1))
        broadcaster.broadcastEnded(snapshot(status = BurstGameRoundStatus.ENDED, totalTapCount = 1, stateVersion = 2))

        Thread.sleep(400)

        verify(chatSseGateway, times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `같은 party의 새 session progress는 이전 ended cleanup 이후에도 전송한다`() {
        val endedStartedAt = LocalDateTime.of(2026, 5, 18, 14, 20)
        val newStartedAt = endedStartedAt.plusMinutes(1)

        broadcaster.broadcastEnded(
            snapshot(
                status = BurstGameRoundStatus.ENDED,
                totalTapCount = 1,
                stateVersion = 2,
                startedAt = endedStartedAt,
            ),
        )
        broadcaster.broadcastProgress(snapshot(totalTapCount = 2, stateVersion = 1, startedAt = newStartedAt))

        verify(chatSseGateway, timeout(1_000).times(2)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    private fun anyEvent(): Set<ResponseBodyEmitter.DataWithMediaType> = any()

    private fun snapshot(
        status: BurstGameRoundStatus = BurstGameRoundStatus.ACTIVE,
        totalTapCount: Int = 0,
        stateVersion: Long = 0,
        startedAt: LocalDateTime = LocalDateTime.of(2026, 5, 18, 14, 20),
    ): BurstGameSnapshot =
        BurstGameSnapshot(
            partyId = 1L,
            myParticipantId = 10L,
            status = status,
            startedAt = startedAt,
            endsAt = startedAt.plusSeconds(20),
            totalTapCount = totalTapCount,
            myTapCount = totalTapCount,
            colorChanged = false,
            stateVersion = stateVersion,
            serverTime = startedAt,
            remainingSeconds = 20,
            rankings =
                listOf(
                    BurstGameRankingEntry(
                        rank = 1,
                        participantId = 10L,
                        nickname = "player",
                        characterId = null,
                        characterImageUrl = null,
                        role = "PARTICIPANT",
                        tapCount = totalTapCount,
                    ),
                ),
            winners =
                listOf(
                    BurstGameWinner(
                        participantId = 10L,
                        nickname = "player",
                        characterId = null,
                        characterImageUrl = null,
                        role = "PARTICIPANT",
                        tapCount = totalTapCount,
                    ),
                ),
        )
}
