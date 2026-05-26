package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.AfterTest
import kotlin.test.Test

class CandleBlowEndEventPublisherTest {
    private val eventBroadcaster: CandleBlowEventBroadcaster = mock()
    private val publisher = CandleBlowEndEventPublisher(eventBroadcaster)

    @AfterTest
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `트랜잭션 동기화가 없으면 ended 이벤트를 즉시 발행한다`() {
        val snapshot = endedSnapshot()

        publisher.publishEndedAfterCommit(snapshot)

        verify(eventBroadcaster).broadcastEnded(snapshot)
    }

    @Test
    fun `트랜잭션 동기화가 있으면 ended 이벤트를 커밋 이후 발행한다`() {
        val snapshot = endedSnapshot()
        TransactionSynchronizationManager.initSynchronization()

        publisher.publishEndedAfterCommit(snapshot)

        verify(eventBroadcaster, never()).broadcastEnded(snapshot)
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(eventBroadcaster).broadcastEnded(snapshot)
    }

    @Test
    fun `ended 이벤트 발행 실패는 호출자에게 전파하지 않는다`() {
        val snapshot = endedSnapshot()
        whenever(eventBroadcaster.broadcastEnded(snapshot)).thenThrow(IllegalStateException("failed"))

        assertDoesNotThrow {
            publisher.publishEndedAfterCommit(snapshot)
        }
    }

    private fun endedSnapshot(): CandleBlowSnapshot =
        CandleBlowSnapshot(
            partyId = 1L,
            status = CandleBlowStatus.FINISHED,
            candles = emptyList(),
            remainingCount = 0,
            finishedReason = CandleBlowFinishedReason.TIMEOUT,
        )
}
