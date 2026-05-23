package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyEndRecoveryResult
import com.team2.server.party.application.service.RealtimePartyEndService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RecoverRealtimePartyEndScheduleUseCase(
    private val realtimePartyEndService: RealtimePartyEndService,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(): RealtimePartyEndRecoveryResult {
        val now = LocalDateTime.now(clock)
        realtimePartyEndService.startDueAutomaticEndings(now)
        return RealtimePartyEndRecoveryResult(
            automaticEndSchedules = realtimePartyEndService.findAutomaticEndSchedules(now),
            endingTargets = realtimePartyEndService.findEndingTargets(now),
        )
    }
}
