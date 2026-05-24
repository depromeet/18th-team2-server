package com.team2.server.burstgame.domain.candle

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode

object CandleBlowPolicy {
    const val CANDLE_COUNT = 9
    const val START_DELAY_SECONDS = 35L
    const val DURATION_SECONDS = 45L

    fun validateCandleId(candleId: Int) {
        if (candleId !in 1..CANDLE_COUNT) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }
}
