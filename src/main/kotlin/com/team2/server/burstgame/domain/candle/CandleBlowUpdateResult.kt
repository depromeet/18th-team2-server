package com.team2.server.burstgame.domain.candle

data class CandleBlowUpdateResult(
    val changed: Boolean,
    val finishedNow: Boolean,
    val snapshot: CandleBlowSnapshot,
)
