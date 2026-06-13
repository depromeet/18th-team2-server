package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.StartBurstGameResponse
import com.team2.server.burstgame.application.service.BurstGameSessionService
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.BurstGameStartSideEffectHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class StartBurstGameUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionService: BurstGameSessionService,
    private val startSideEffectHandler: BurstGameStartSideEffectHandler,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): StartBurstGameResponse {
        val resolved = participantResolver.resolveWithParty(partyId, userId, participantToken)
        val now = LocalDateTime.now(clock)
        val result =
            startSideEffectHandler.resolve(
                startResult =
                    sessionService.start(
                        partyId = partyId,
                        hostEnteredAt = resolved.party.hostEnteredAt,
                        participant = resolved.participant,
                        now = now,
                    ),
            )
        startSideEffectHandler.completeStartedAfterCommit(partyId, result, now)
        return StartBurstGameResponse.from(result.snapshot)
    }
}
