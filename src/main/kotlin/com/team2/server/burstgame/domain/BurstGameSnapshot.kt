package com.team2.server.burstgame.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max

data class BurstGameSnapshot(
    val partyId: Long,
    val myParticipantId: Long,
    val status: BurstGameRoundStatus,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val totalTapCount: Int,
    val myTapCount: Int,
    val colorChanged: Boolean,
    val stateVersion: Long,
    val serverTime: LocalDateTime,
    val remainingSeconds: Long,
    val rankings: List<BurstGameRankingEntry>,
) {
    companion object {
        fun remainingSeconds(
            startedAt: LocalDateTime,
            endsAt: LocalDateTime,
            serverTime: LocalDateTime,
        ): Long {
            val playableFrom = maxOf(serverTime, startedAt)
            val millis = Duration.between(playableFrom, endsAt).toMillis()
            return max(0.0, ceil(millis / MILLIS_PER_SECOND)).toLong()
        }

        private const val MILLIS_PER_SECOND = 1_000.0
    }
}
