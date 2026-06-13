package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class CandleBlowEndEventPublisher(
    private val eventBroadcaster: CandleBlowEventBroadcaster,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishEndedAfterCommit(snapshot: CandleBlowSnapshot) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishEnded(snapshot)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    publishEnded(snapshot)
                }
            },
        )
    }

    private fun publishEnded(snapshot: CandleBlowSnapshot) {
        runCatching {
            eventBroadcaster.broadcastEnded(snapshot)
        }.onFailure { ex ->
            log.error("Failed to broadcast candle blow end after state lookup. partyId={}", snapshot.partyId, ex)
        }
    }
}
