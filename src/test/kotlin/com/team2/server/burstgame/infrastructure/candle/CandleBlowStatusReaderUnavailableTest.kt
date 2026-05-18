package com.team2.server.burstgame.infrastructure.candle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class CandleBlowStatusReaderUnavailableTest {
    @Test
    fun `prod reader가 설정되지 않으면 fail fast 한다`() {
        val ex =
            assertFailsWith<IllegalStateException> {
                CandleBlowStatusReaderUnavailable().isCandleBlowCompleted(1L)
            }

        assertContains(ex.message.orEmpty(), "partyId=1")
    }
}
