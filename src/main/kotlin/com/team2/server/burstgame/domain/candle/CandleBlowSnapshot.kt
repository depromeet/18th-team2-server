package com.team2.server.burstgame.domain.candle

data class CandleBlowSnapshot(
    val partyId: Long,
    val status: CandleBlowStatus,
    val candles: List<CandleState>,
    val remainingCount: Int,
    val finishedReason: CandleBlowFinishedReason?,
)
