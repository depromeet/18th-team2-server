package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleState
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class SseCandleBlowEventBroadcaster(
    private val chatSseGateway: ChatSseGateway,
) : CandleBlowEventBroadcaster {
    private val partyLocks = Array(LOCK_STRIPE_COUNT) { Any() }
    private val startedParties = ConcurrentHashMap.newKeySet<Long>()
    private val endedParties = ConcurrentHashMap.newKeySet<Long>()

    override fun broadcastStarted(snapshot: CandleBlowSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            if (snapshot.partyId in endedParties) return
            if (!startedParties.add(snapshot.partyId)) return
            emit(snapshot.partyId, EVENT_STARTED, CandleBlowPayload.from(snapshot))
        }
    }

    override fun broadcastProgress(snapshot: CandleBlowSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            if (snapshot.partyId in endedParties) return
            emit(snapshot.partyId, EVENT_PROGRESS, CandleBlowPayload.from(snapshot))
        }
    }

    override fun broadcastEnded(snapshot: CandleBlowSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            if (!endedParties.add(snapshot.partyId)) return
            emit(snapshot.partyId, EVENT_ENDED, CandleBlowPayload.from(snapshot))
        }
    }

    private fun emit(
        partyId: Long,
        eventName: String,
        payload: Any,
    ) {
        chatSseGateway.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name(eventName)
                .data(payload)
                .build(),
        )
    }

    private fun lockFor(partyId: Long): Any = partyLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    data class CandleBlowPayload(
        val partyId: Long,
        val status: String,
        val candles: List<CandlePayload>,
        val finishedReason: String?,
    ) {
        companion object {
            fun from(snapshot: CandleBlowSnapshot): CandleBlowPayload =
                CandleBlowPayload(
                    partyId = snapshot.partyId,
                    status = snapshot.status.name,
                    candles = snapshot.candles.map { CandlePayload.from(it) },
                    finishedReason = snapshot.finishedReason?.name,
                )
        }
    }

    data class CandlePayload(
        val candleId: Int,
        val extinguished: Boolean,
    ) {
        companion object {
            fun from(candle: CandleState): CandlePayload =
                CandlePayload(
                    candleId = candle.candleId,
                    extinguished = candle.extinguished,
                )
        }
    }

    companion object {
        private const val EVENT_STARTED = "candle-blow-started"
        private const val EVENT_PROGRESS = "candle-blow-progress"
        private const val EVENT_ENDED = "candle-blow-ended"
        private const val LOCK_STRIPE_COUNT = 64
    }
}
