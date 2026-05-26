package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus

data class CandleBlowScheduleResult(
    val partyId: Long,
    val status: CandleBlowStatus,
    val finishedReason: CandleBlowFinishedReason?,
) {
    companion object {
        fun from(snapshot: CandleBlowSnapshot): CandleBlowScheduleResult =
            CandleBlowScheduleResult(
                partyId = snapshot.partyId,
                status = snapshot.status,
                finishedReason = snapshot.finishedReason,
            )
    }
}
