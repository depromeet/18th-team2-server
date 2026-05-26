package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.port.BurstGameEventBroadcaster
import com.team2.server.burstgame.domain.BurstGameRankingEntry
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.chat.application.port.PartySseEventPublisher
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
    private val partySseEventPublisher: PartySseEventPublisher,
) : BurstGameEventBroadcaster {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "burst-game-broadcaster")
        }
    private val pendingProgress = ConcurrentHashMap<Long, BurstGameSnapshot>()
    private val scheduledProgress = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val roundLocks = Array(LOCK_STRIPE_COUNT) { Any() }
    private val endedRounds = ConcurrentHashMap<Long, LocalDateTime>()

    override fun broadcastStarted(snapshot: BurstGameSnapshot) {
        emit(snapshot.partyId, EVENT_STARTED, BurstGameStartedPayload.from(snapshot))
    }

    override fun broadcastProgress(snapshot: BurstGameSnapshot) {
        val lock = lockFor(snapshot.partyId)
        synchronized(lock) {
            val endedStartedAt = endedRounds[snapshot.partyId]
            if (endedStartedAt == snapshot.startedAt) {
                return
            }
            if (endedStartedAt != null) {
                endedRounds.remove(snapshot.partyId, endedStartedAt)
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
            endedRounds[snapshot.partyId] = snapshot.startedAt
            pendingProgress.remove(snapshot.partyId)
            scheduledProgress.remove(snapshot.partyId)?.cancel(false)
            emit(snapshot.partyId, EVENT_ENDED, BurstGameEndedPayload.from(snapshot))
        }
        scheduleEndedRoundCleanup(snapshot.partyId, snapshot.startedAt, lock)
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
                        if (latest != null && endedRounds[partyId] != latest.startedAt) {
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
        startedAt: LocalDateTime,
        lock: Any,
    ) {
        executor.schedule(
            {
                synchronized(lock) {
                    endedRounds.remove(partyId, startedAt)
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
        partySseEventPublisher.broadcastAfterCommit(
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

    private fun lockFor(partyId: Long): Any = roundLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    data class BurstGameStartedPayload(
        val partyId: Long,
        val status: String,
        val startedAt: LocalDateTime,
        val endsAt: LocalDateTime,
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
                    colorChanged = snapshot.colorChanged,
                    stateVersion = snapshot.stateVersion,
                    serverTime = snapshot.serverTime,
                )
        }
    }

    data class BurstGameProgressPayload(
        val partyId: Long,
        val colorChanged: Boolean,
        val endsAt: LocalDateTime,
        val stateVersion: Long,
        val serverTime: LocalDateTime,
        val rankings: List<RankingPayload>,
    ) {
        companion object {
            fun from(snapshot: BurstGameSnapshot): BurstGameProgressPayload =
                BurstGameProgressPayload(
                    partyId = snapshot.partyId,
                    colorChanged = snapshot.colorChanged,
                    endsAt = snapshot.endsAt,
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
        val rankings: List<RankingPayload>,
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
                    rankings = snapshot.rankings.map { RankingPayload.from(it) },
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
            fun from(entry: BurstGameRankingEntry): RankingPayload =
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

    companion object {
        private const val EVENT_STARTED = "burst-game-started"
        private const val EVENT_PROGRESS = "burst-game-progress"
        private const val EVENT_ENDED = "burst-game-ended"
        private const val PROGRESS_THROTTLE_MILLIS = 250L
        private const val LOCK_STRIPE_COUNT = 64
    }
}
