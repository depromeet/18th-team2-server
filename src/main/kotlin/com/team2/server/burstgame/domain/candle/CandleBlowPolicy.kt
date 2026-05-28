package com.team2.server.burstgame.domain.candle

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import java.time.Duration

object CandleBlowPolicy {
    const val CANDLE_COUNT = 9
    const val START_DELAY_SECONDS = 41L
    const val DURATION_SECONDS = 45L
    private const val SESSION_TTL_MINUTES = 10L
    val SESSION_TTL: Duration = Duration.ofMinutes(SESSION_TTL_MINUTES)

    fun validateCandleId(candleId: Int) {
        if (candleId !in 1..CANDLE_COUNT) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }
}
