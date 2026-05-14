package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDate
import java.time.LocalDateTime

data class PartyInviteLookupResult(
    val partyId: Long,
    val celebrantNickname: String?,
    val isHost: Boolean,
    val partyOption: PartyOption,
    val partyEnded: Boolean,
    val rollingPaperWritten: Boolean,
    val partyStartDate: LocalDate,
    val partyEndDate: LocalDate,
    val realtimeSchedule: RealtimeScheduleResult?,
)

data class RealtimeScheduleResult(
    val liveStartAt: LocalDateTime,
    val enterableFrom: LocalDateTime,
    val liveEndAt: LocalDateTime,
    val liveDurationMinutes: Long,
)
