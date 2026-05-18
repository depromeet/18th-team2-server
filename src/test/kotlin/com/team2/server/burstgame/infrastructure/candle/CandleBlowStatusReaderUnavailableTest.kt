package com.team2.server.burstgame.infrastructure.candle

import kotlin.test.Test
import kotlin.test.assertFalse

class CandleBlowStatusReaderUnavailableTest {
    @Test
    fun `prod reader가 설정되지 않으면 완료되지 않음으로 반환한다`() {
        assertFalse(CandleBlowStatusReaderUnavailable().isCandleBlowCompleted(1L))
    }
}
