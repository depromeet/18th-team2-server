package com.team2.server.party.application.dto

import java.time.LocalDate
import java.time.LocalTime

data class CreateRealtimePartyCommand(
    val celebrantNickname: String,
    val startedDate: LocalDate,
    val startTime: LocalTime,
    val characterId: Long,
)
