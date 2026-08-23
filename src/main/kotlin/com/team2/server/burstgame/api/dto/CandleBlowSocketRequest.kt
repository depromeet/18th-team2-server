package com.team2.server.burstgame.api.dto

import jakarta.validation.constraints.NotBlank

data class CandleBlowSocketRequest(
    @field:NotBlank
    val participantToken: String,
    @field:NotBlank
    val clientRequestId: String,
)
