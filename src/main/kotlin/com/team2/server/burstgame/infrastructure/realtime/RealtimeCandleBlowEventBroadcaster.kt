package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.port.CandleBlowEventBroadcaster
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleState
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 촛불끄기 진행 이벤트를 SSE와 WebSocket 양쪽에 브로드캐스트한다.
 *
 * started/ended 중복 방지, 정리(cleanup) 락 등 전송 방식과 무관한 로직을 한 곳에서만 관리하고,
 * [emit]만 두 게이트웨이로 나눠 보낸다 — 전송 방식별로 이 클래스를 통째로 복제하면 중복 방지 로직이
 * 두 벌이 된다.
 */
@Component
class RealtimeCandleBlowEventBroadcaster(
    private val chatSseGateway: ChatSseGateway,
    private val chatSocketGateway: ChatSocketGateway,
) : CandleBlowEventBroadcaster {
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "candle-blow-broadcaster")
        }
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
        scheduleDedupeCleanup(snapshot.partyId, lock)
    }

    private fun scheduleDedupeCleanup(
        partyId: Long,
        lock: Any,
    ) {
        executor.schedule(
            {
                synchronized(lock) {
                    startedParties.remove(partyId)
                    endedParties.remove(partyId)
                }
            },
            CandleBlowPolicy.SESSION_TTL.toMillis(),
            TimeUnit.MILLISECONDS,
        )
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
        chatSocketGateway.broadcastAfterCommit(partyId, eventName, payload)
    }

    private fun lockFor(partyId: Long): Any = partyLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

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
