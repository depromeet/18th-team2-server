package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowScheduleTarget
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.party.application.usecase.FindRealtimePartiesWaitingAutomaticEndingUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RecoverCandleBlowScheduleUseCase(
    private val findRealtimePartiesWaitingAutomaticEndingUseCase: FindRealtimePartiesWaitingAutomaticEndingUseCase,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(): List<CandleBlowScheduleTarget> {
        val now = LocalDateTime.now(clock)
        val startedAfter =
            now.minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + CandleBlowPolicy.DURATION_SECONDS)
        return findRealtimePartiesWaitingAutomaticEndingUseCase(startedAfter)
            .map { party ->
                CandleBlowScheduleTarget(
                    partyId = party.id,
                    partyStartedAt = party.startedAt,
                )
            }
    }
}
