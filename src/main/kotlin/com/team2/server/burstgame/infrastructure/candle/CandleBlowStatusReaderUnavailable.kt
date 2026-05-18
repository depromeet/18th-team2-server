package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.service.CandleBlowStatusReader
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod")
class CandleBlowStatusReaderUnavailable : CandleBlowStatusReader {
    override fun isCandleBlowCompleted(partyId: Long): Boolean =
        throw IllegalStateException("CandleBlowStatusReader is not configured for prod. partyId=$partyId")
}
