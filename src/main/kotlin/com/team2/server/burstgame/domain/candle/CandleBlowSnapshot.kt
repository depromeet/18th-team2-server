package com.team2.server.burstgame.domain.candle

data class CandleBlowSnapshot(
    val partyId: Long,
    val status: CandleBlowStatus,
    val candles: List<CandleState>,
    val remainingCount: Int,
    val finishedReason: CandleBlowFinishedReason?,
) {
    companion object {
        fun waiting(partyId: Long): CandleBlowSnapshot =
            CandleBlowSnapshot(
                partyId = partyId,
                status = CandleBlowStatus.WAITING,
                candles =
                    (1..CandleBlowPolicy.CANDLE_COUNT).map { candleId ->
                        CandleState(candleId = candleId, extinguished = false)
                    },
                remainingCount = CandleBlowPolicy.CANDLE_COUNT,
                finishedReason = null,
            )
    }
}
