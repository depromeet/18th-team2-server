package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowScheduleTarget
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import com.team2.server.party.application.usecase.FindRealtimePartiesWithHostEnteredUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RecoverCandleBlowScheduleUseCase(
    private val findRealtimePartiesWithHostEnteredUseCase: FindRealtimePartiesWithHostEnteredUseCase,
    private val candleBlowProperties: CandleBlowProperties,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(): List<CandleBlowScheduleTarget> {
        val now = LocalDateTime.now(clock)
        val hostEnteredAfter =
            now.minusSeconds(CandleBlowPolicy.START_DELAY_SECONDS + candleBlowProperties.durationSeconds)
        return findRealtimePartiesWithHostEnteredUseCase(hostEnteredAfter)
            .map { party ->
                CandleBlowScheduleTarget(
                    partyId = party.partyId,
                    hostEnteredAt = party.hostEnteredAt,
                )
            }
    }
}
