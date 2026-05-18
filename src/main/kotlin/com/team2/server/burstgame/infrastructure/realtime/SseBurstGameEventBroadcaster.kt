package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class SseBurstGameEventBroadcaster(
    private val chatSseGateway: ChatSseGateway,
) : BurstGameEventBroadcaster {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val pendingProgress = ConcurrentHashMap<Long, BurstGameSnapshot>()
    private val scheduledProgress = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val roundLocks = ConcurrentHashMap<Long, Any>()
    private val endedRounds = ConcurrentHashMap.newKeySet<Long>()

    override fun broadcastStarted(snapshot: BurstGameSnapshot) {
        emit(snapshot.partyId, EVENT_STARTED, BurstGameStartedPayload.from(snapshot))
    }

    override fun broadcastProgress(snapshot: BurstGameSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            if (snapshot.partyId in endedRounds) {
                return
            }
            pendingProgress[snapshot.partyId] = snapshot
            scheduledProgress.computeIfAbsent(snapshot.partyId) { partyId ->
                scheduleProgress(partyId, lock)
            }
        }
    }

    override fun broadcastEnded(snapshot: BurstGameSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            endedRounds.add(snapshot.partyId)
            pendingProgress.remove(snapshot.partyId)
            scheduledProgress.remove(snapshot.partyId)?.cancel(false)
            emit(snapshot.partyId, EVENT_ENDED, BurstGameEndedPayload.from(snapshot))
        }
        scheduleEndedRoundCleanup(snapshot.partyId, lock)
    }

    private fun scheduleProgress(
        partyId: Long,
        lock: Any,
    ): ScheduledFuture<*> =
        executor.schedule(
            {
                runCatching {
                    synchronized(lock) {
                        val latest = pendingProgress.remove(partyId)
                        scheduledProgress.remove(partyId)
                        if (latest != null && partyId !in endedRounds) {
                            emit(latest.partyId, EVENT_PROGRESS, BurstGameProgressPayload.from(latest))
                        }
                    }
                }.onFailure { ex ->
                    log.error("Failed to broadcast burst game progress. partyId={}", partyId, ex)
                }
            },
            PROGRESS_THROTTLE_MILLIS,
            TimeUnit.MILLISECONDS,
        )

    private fun scheduleEndedRoundCleanup(
        partyId: Long,
        lock: Any,
    ) {
        executor.schedule(
            {
                synchronized(lock) {
                    endedRounds.remove(partyId)
                    roundLocks.remove(partyId, lock)
                }
            },
            PROGRESS_THROTTLE_MILLIS,
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
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private fun lockFor(partyId: Long): Any = roundLocks.computeIfAbsent(partyId) { Any() }

    data class BurstGameStartedPayload(
        val partyId: Long,
        val status: String,
        val startedAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val totalTapCount: Int,
        val colorChanged: Boolean,
        val stateVersion: Long,
        val serverTime: LocalDateTime,
    ) {
        companion object {
            fun from(snapshot: BurstGameSnapshot): BurstGameStartedPayload =
                BurstGameStartedPayload(
                    partyId = snapshot.partyId,
                    status = snapshot.status.name,
                    startedAt = snapshot.startedAt,
                    endsAt = snapshot.endsAt,
                    totalTapCount = snapshot.totalTapCount,
                    colorChanged = snapshot.colorChanged,
                    stateVersion = snapshot.stateVersion,
                    serverTime = snapshot.serverTime,
                )
        }
    }

    data class BurstGameProgressPayload(
        val partyId: Long,
        val totalTapCount: Int,
        val colorChanged: Boolean,
        val remainingSeconds: Long,
        val stateVersion: Long,
        val serverTime: LocalDateTime,
        val rankings: List<RankingPayload>,
    ) {
        companion object {
            fun from(snapshot: BurstGameSnapshot): BurstGameProgressPayload =
                BurstGameProgressPayload(
                    partyId = snapshot.partyId,
                    totalTapCount = snapshot.totalTapCount,
                    colorChanged = snapshot.colorChanged,
                    remainingSeconds = snapshot.remainingSeconds,
                    stateVersion = snapshot.stateVersion,
                    serverTime = snapshot.serverTime,
                    rankings = snapshot.rankings.map { RankingPayload.from(it) },
                )
        }
    }

    data class BurstGameEndedPayload(
        val partyId: Long,
        val status: String,
        val endsAt: LocalDateTime,
        val totalTapCount: Int,
        val colorChanged: Boolean,
        val stateVersion: Long,
        val serverTime: LocalDateTime,
        val winners: List<WinnerPayload>,
    ) {
        companion object {
            fun from(snapshot: BurstGameSnapshot): BurstGameEndedPayload =
                BurstGameEndedPayload(
                    partyId = snapshot.partyId,
                    status = snapshot.status.name,
                    endsAt = snapshot.endsAt,
                    totalTapCount = snapshot.totalTapCount,
                    colorChanged = snapshot.colorChanged,
                    stateVersion = snapshot.stateVersion,
                    serverTime = snapshot.serverTime,
                    winners = snapshot.winners.map { WinnerPayload.from(it) },
                )
        }
    }

    data class RankingPayload(
        val rank: Int,
        val participantId: Long,
        val nickname: String,
        val characterId: Long?,
        val characterImageUrl: String?,
        val role: String,
        val tapCount: Int,
    ) {
        companion object {
            fun from(entry: com.team2.server.burstgame.domain.BurstGameRankingEntry): RankingPayload =
                RankingPayload(
                    rank = entry.rank,
                    participantId = entry.participantId,
                    nickname = entry.nickname,
                    characterId = entry.characterId,
                    characterImageUrl = entry.characterImageUrl,
                    role = entry.role,
                    tapCount = entry.tapCount,
                )
        }
    }

    data class WinnerPayload(
        val participantId: Long,
        val nickname: String,
        val characterId: Long?,
        val characterImageUrl: String?,
        val role: String,
        val tapCount: Int,
    ) {
        companion object {
            fun from(winner: com.team2.server.burstgame.domain.BurstGameWinner): WinnerPayload =
                WinnerPayload(
                    participantId = winner.participantId,
                    nickname = winner.nickname,
                    characterId = winner.characterId,
                    characterImageUrl = winner.characterImageUrl,
                    role = winner.role,
                    tapCount = winner.tapCount,
                )
        }
    }

    companion object {
        private const val EVENT_STARTED = "burst-game-started"
        private const val EVENT_PROGRESS = "burst-game-progress"
        private const val EVENT_ENDED = "burst-game-ended"
        private const val PROGRESS_THROTTLE_MILLIS = 250L
    }
}
