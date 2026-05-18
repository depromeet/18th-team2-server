package com.team2.server.burstgame.domain.policy

import java.time.Duration

object BurstGamePolicy {
    const val ROUND_DURATION_SECONDS = 20L
    const val COLOR_CHANGE_TAP_COUNT = 100
    const val MAX_BATCH_TAP_COUNT = 30L
    const val TOKEN_REFILL_PER_SECOND = 20
    const val TOKEN_BUCKET_CAPACITY = 30
    const val MAX_PARTICIPANT_ROUND_TAP_COUNT = 400
    const val MAX_SEQUENCE_GAP = 1_000L
    private const val ENDED_SESSION_TTL_MINUTES = 5L
    val ENDED_SESSION_TTL: Duration = Duration.ofMinutes(ENDED_SESSION_TTL_MINUTES)
}
