package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank

data class LeaveChatSocketRequest(
    @field:NotBlank
    val participantToken: String,
    @field:NotBlank
    val clientRequestId: String,
)
