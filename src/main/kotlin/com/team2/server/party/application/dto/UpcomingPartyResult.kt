package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDateTime

data class UpcomingPartyResult(
    val partyId: Long,
    val inviteToken: String?,
    val partyOption: PartyOption,
    val celebrantNickname: String?,
    val partyStartedAt: LocalDateTime,
    val partyEndedAt: LocalDateTime,
    val isHost: Boolean,
    val rollingPaperWritten: Boolean,
    val hostRollingPaperOpenAt: LocalDateTime?,
    val realtimeSchedule: UpcomingRealtimeScheduleResult?,
)

data class UpcomingRealtimeScheduleResult(
    val enterableFrom: LocalDateTime,
    val liveStartAt: LocalDateTime,
    val liveEndAt: LocalDateTime,
)
