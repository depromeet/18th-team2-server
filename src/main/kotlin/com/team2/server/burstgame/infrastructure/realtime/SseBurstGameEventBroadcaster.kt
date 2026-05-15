package com.team2.server.burstgame.infrastructure.realtime

import com.team2.server.burstgame.application.service.BurstGameEventBroadcaster
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import jakarta.annotation.PreDestroy
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
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val pendingProgress = ConcurrentHashMap<String, BurstGameSnapshot>()
    private val scheduledProgress = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override fun broadcastStarted(snapshot: BurstGameSnapshot) {
        emit(snapshot.partyId, EVENT_STARTED, BurstGameStartedPayload.from(snapshot))
    }

    override fun broadcastProgress(snapshot: BurstGameSnapshot) {
        pendingProgress[snapshot.roundId] = snapshot
        scheduledProgress.computeIfAbsent(snapshot.roundId) { roundId ->
            executor.schedule(
                {
                    val latest = pendingProgress.remove(roundId)
                    scheduledProgress.remove(roundId)
                    if (latest != null) {
                        emit(latest.partyId, EVENT_PROGRESS, BurstGameProgressPayload.from(latest))
                    }
                },
                PROGRESS_THROTTLE_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    override fun broadcastEnded(snapshot: BurstGameSnapshot) {
        pendingProgress.remove(snapshot.roundId)
        scheduledProgress.remove(snapshot.roundId)?.cancel(false)
        emit(snapshot.partyId, EVENT_ENDED, BurstGameEndedPayload.from(snapshot))
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

    data class BurstGameStartedPayload(
        val roundId: String,
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
                    roundId = snapshot.roundId,
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
        val roundId: String,
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
                    roundId = snapshot.roundId,
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
        val roundId: String,
        val partyId: Long,
        val status: String,
        val endsAt: LocalDateTime,
        val totalTapCount: Int,
        val colorChanged: Boolean,
        val stateVersion: Long,
        val serverTime: LocalDateTime,
        val rankings: List<RankingPayload>,
        val winners: List<WinnerPayload>,
    ) {
        companion object {
            fun from(snapshot: BurstGameSnapshot): BurstGameEndedPayload =
                BurstGameEndedPayload(
                    roundId = snapshot.roundId,
                    partyId = snapshot.partyId,
                    status = snapshot.status.name,
                    endsAt = snapshot.endsAt,
                    totalTapCount = snapshot.totalTapCount,
                    colorChanged = snapshot.colorChanged,
                    stateVersion = snapshot.stateVersion,
                    serverTime = snapshot.serverTime,
                    rankings = snapshot.rankings.map { RankingPayload.from(it) },
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
