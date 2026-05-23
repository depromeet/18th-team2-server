package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.RealtimeParty
import java.time.LocalDateTime

data class RealtimePartyEndStartResult(
    val affected: Int,
    val party: RealtimeParty,
)

data class RealtimeEndingScheduleTarget(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val startedNow: Boolean = false,
)

data class RealtimeAutomaticEndSchedule(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
)

data class RealtimeHostEndAvailableSchedule(
    val partyId: Long,
    val startedAt: LocalDateTime,
)

data class RealtimePartyEndRecoverySchedules(
    val hostEndAvailableSchedules: List<RealtimeHostEndAvailableSchedule>,
    val automaticEndSchedules: List<RealtimeAutomaticEndSchedule>,
)

data class RealtimePartyEndRecoveryResult(
    val hostEndAvailableSchedules: List<RealtimeHostEndAvailableSchedule>,
    val automaticEndSchedules: List<RealtimeAutomaticEndSchedule>,
    val endingTargets: List<RealtimeEndingScheduleTarget>,
)
