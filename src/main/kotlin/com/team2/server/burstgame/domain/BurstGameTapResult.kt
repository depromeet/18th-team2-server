package com.team2.server.burstgame.domain

data class BurstGameTapResult(
    val accepted: Boolean,
    val ignoredReason: BurstGameTapIgnoredReason?,
    val snapshot: BurstGameSnapshot,
    val endedNow: Boolean = false,
) {
    init {
        require(accepted == (ignoredReason == null)) {
            "Inconsistent tap result. accepted=$accepted ignoredReason=$ignoredReason"
        }
    }
}
