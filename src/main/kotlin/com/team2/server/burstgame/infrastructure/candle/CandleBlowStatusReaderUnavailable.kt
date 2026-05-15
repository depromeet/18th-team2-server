package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.service.CandleBlowStatusReader
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod")
class CandleBlowStatusReaderUnavailable : CandleBlowStatusReader {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun isCandleBlowCompleted(partyId: Long): Boolean {
        log.error("CandleBlowStatusReader is not configured for prod. partyId={}", partyId)
        return false
    }
}
