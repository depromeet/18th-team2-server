package com.team2.server.party.application.dto

import java.time.LocalDate

data class CreatePaperOnlyPartyCommand(
    val celebrantNickname: String,
    val startedDate: LocalDate,
)
