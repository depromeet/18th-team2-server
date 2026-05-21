package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "dev", "test")
class CandleBlowStatusReaderStub : CandleBlowStatusReader {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun isCandleBlowCompleted(partyId: Long): Boolean {
        log.warn("Using CandleBlowStatusReaderStub. partyId={}", partyId)
        return true
    }
}
