package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.burstgame.domain.candle.CandleState
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SseCandleBlowEventBroadcasterTest {
    private val chatSseGateway: ChatSseGateway = mock()
    private val broadcaster = SseCandleBlowEventBroadcaster(chatSseGateway)

    @AfterTest
    fun tearDown() {
        broadcaster.shutdown()
    }

    @Test
    fun `started 이벤트는 한 번만 전송한다`() {
        val snapshot = snapshot()

        broadcaster.broadcastStarted(snapshot)
        broadcaster.broadcastStarted(snapshot)

        verify(chatSseGateway, times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `progress 이벤트 payload는 촛불 상태를 포함한다`() {
        val snapshot = snapshot(extinguishedCandleIds = setOf(1, 3))

        broadcaster.broadcastProgress(snapshot)

        val eventCaptor = argumentCaptor<Set<ResponseBodyEmitter.DataWithMediaType>>()
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), eventCaptor.capture(), isNull())
        val payload =
            eventCaptor.firstValue
                .map { it.data }
                .filterIsInstance<SseCandleBlowEventBroadcaster.CandleBlowPayload>()
                .single()
        assertEquals("ACTIVE", payload.status)
        assertEquals(true, payload.candles[0].extinguished)
        assertEquals(false, payload.candles[1].extinguished)
        assertEquals(true, payload.candles[2].extinguished)
    }

    @Test
    fun `ended 이후 progress는 전송하지 않는다`() {
        broadcaster.broadcastEnded(
            snapshot(
                status = CandleBlowStatus.FINISHED,
                finishedReason = CandleBlowFinishedReason.ALL_EXTINGUISHED,
            ),
        )
        broadcaster.broadcastProgress(snapshot(extinguishedCandleIds = setOf(1)))

        verify(chatSseGateway, timeout(300).times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    @Test
    fun `ended 이벤트는 한 번만 전송한다`() {
        val snapshot =
            snapshot(
                status = CandleBlowStatus.FINISHED,
                finishedReason = CandleBlowFinishedReason.TIMEOUT,
            )

        broadcaster.broadcastEnded(snapshot)
        broadcaster.broadcastEnded(snapshot)

        verify(chatSseGateway, times(1)).broadcastAfterCommit(eq(1L), anyEvent(), isNull())
    }

    private fun anyEvent(): Set<ResponseBodyEmitter.DataWithMediaType> = any()

    private fun snapshot(
        status: CandleBlowStatus = CandleBlowStatus.ACTIVE,
        extinguishedCandleIds: Set<Int> = emptySet(),
        finishedReason: CandleBlowFinishedReason? = null,
    ): CandleBlowSnapshot =
        CandleBlowSnapshot(
            partyId = 1L,
            status = status,
            candles =
                (1..9).map { candleId ->
                    CandleState(
                        candleId = candleId,
                        extinguished = candleId in extinguishedCandleIds,
                    )
                },
            remainingCount = 9 - extinguishedCandleIds.size,
            finishedReason = finishedReason,
        )
}
