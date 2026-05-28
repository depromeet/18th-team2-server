package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CandleBlowUpdateEventPublisher(
    private val eventBroadcaster: CandleBlowEventBroadcaster,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(
        changed: Boolean,
        shouldBroadcastEnded: Boolean,
        snapshot: CandleBlowSnapshot,
    ) {
        if (!changed && !shouldBroadcastEnded) return
        runCatching {
            if (shouldBroadcastEnded) {
                eventBroadcaster.broadcastEnded(snapshot)
            } else {
                eventBroadcaster.broadcastProgress(snapshot)
            }
        }.onFailure { ex ->
            log.error("Failed to broadcast candle blow update. partyId={}", snapshot.partyId, ex)
        }
    }
}
