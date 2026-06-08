package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.usecase.StartBurstGameUseCase
import com.team2.server.party.application.port.BurstGameStartPort
import org.springframework.stereotype.Component

@Component
class PhaseAdvanceBurstGameStarter(
    private val startBurstGameUseCase: StartBurstGameUseCase,
) : BurstGameStartPort {
    override fun start(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        startBurstGameUseCase(partyId, userId, participantToken)
    }
}
