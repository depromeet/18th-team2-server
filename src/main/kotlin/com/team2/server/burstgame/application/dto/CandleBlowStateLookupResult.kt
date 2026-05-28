package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot

data class CandleBlowStateLookupResult(
    val response: CandleBlowResponse,
    val endedSnapshot: CandleBlowSnapshot?,
)
