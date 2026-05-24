package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.candle.CandleState
import io.swagger.v3.oas.annotations.media.Schema

data class CandleBlowCandleResponse(
    @Schema(description = "촛불 번호입니다. 1부터 9까지 고정입니다.", example = "1")
    val candleId: Int,
    @Schema(description = "촛불이 꺼졌는지 여부입니다.", example = "true")
    val extinguished: Boolean,
) {
    companion object {
        fun from(candle: CandleState): CandleBlowCandleResponse =
            CandleBlowCandleResponse(
                candleId = candle.candleId,
                extinguished = candle.extinguished,
            )
    }
}
