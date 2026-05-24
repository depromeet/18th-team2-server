package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.candle.CandleBlowFinishedReason
import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import io.swagger.v3.oas.annotations.media.Schema

data class CandleBlowStateResponse(
    @Schema(description = "촛불끄기 단계가 속한 파티 ID입니다.", example = "10")
    val partyId: Long,
    @Schema(description = "촛불끄기 상태입니다.", example = "ACTIVE", allowableValues = ["WAITING", "ACTIVE", "FINISHED"])
    val status: CandleBlowStatus,
    @Schema(description = "1부터 9까지의 촛불 상태입니다.")
    val candles: List<CandleBlowCandleResponse>,
    @Schema(
        description = "종료 사유입니다. 종료 전에는 null입니다.",
        example = "ALL_EXTINGUISHED",
        allowableValues = ["ALL_EXTINGUISHED", "TIMEOUT"],
    )
    val finishedReason: CandleBlowFinishedReason?,
) {
    companion object {
        fun from(snapshot: CandleBlowSnapshot): CandleBlowStateResponse =
            CandleBlowStateResponse(
                partyId = snapshot.partyId,
                status = snapshot.status,
                candles = snapshot.candles.map { CandleBlowCandleResponse.from(it) },
                finishedReason = snapshot.finishedReason,
            )
    }
}
