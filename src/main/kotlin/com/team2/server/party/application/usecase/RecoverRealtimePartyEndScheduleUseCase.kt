package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndRecoveryResult
import com.team2.server.party.application.service.RealtimePartyAutomaticEndSchedule
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.application.service.RealtimePartyEndingSchedule
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
            automaticEndSchedules = findAutomaticEndSchedules(now),
            endingTargets = findEndingTargets(now),
        )
    }

    private fun findAutomaticEndSchedules(now: LocalDateTime): List<RealtimeAutomaticEndSchedule> =
        realtimePartyEndService.findAutomaticEndSchedules(now).map { it.toResult() }

    private fun findEndingTargets(now: LocalDateTime): List<RealtimeEndingScheduleTarget> =
        realtimePartyEndService.findEndingTargets(now).map { it.toResult() }

    private fun RealtimePartyAutomaticEndSchedule.toResult(): RealtimeAutomaticEndSchedule =
        RealtimeAutomaticEndSchedule(
            partyId = partyId,
            endingStartedAt = endingStartedAt,
        )

    private fun RealtimePartyEndingSchedule.toResult(): RealtimeEndingScheduleTarget =
        RealtimeEndingScheduleTarget(
            partyId = partyId,
            endingStartedAt = endingStartedAt,
            endedAt = endedAt,
            startedNow = startedNow,
        )
}
