package com.team2.server.fireworks.api.dto

import jakarta.validation.constraints.NotBlank

data class FireworksSocketRequest(
    @field:NotBlank
    val participantToken: String,
    @field:NotBlank
    val clientRequestId: String,
)
