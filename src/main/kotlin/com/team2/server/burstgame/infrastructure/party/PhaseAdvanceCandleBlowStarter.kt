package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.usecase.StartCandleBlowUseCase
import com.team2.server.party.application.port.CandleBlowStartPort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PhaseAdvanceCandleBlowStarter(
    private val startCandleBlowUseCase: StartCandleBlowUseCase,
) : CandleBlowStartPort {
    override fun start(
        partyId: Long,
        startedAt: LocalDateTime,
    ) {
        startCandleBlowUseCase(partyId, startedAt)
    }
}
