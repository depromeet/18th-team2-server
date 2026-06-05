package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.BurstGameSnapshot

sealed interface BurstGameStartResult {
    val snapshot: BurstGameSnapshot

    data class Started(
        override val snapshot: BurstGameSnapshot,
        val created: Boolean,
    ) : BurstGameStartResult

    data class AlreadyEnded(
        override val snapshot: BurstGameSnapshot,
        val endedNow: Boolean,
    ) : BurstGameStartResult
}

data class BurstGameSnapshotResult(
    val snapshot: BurstGameSnapshot,
    val endedNow: Boolean,
)
