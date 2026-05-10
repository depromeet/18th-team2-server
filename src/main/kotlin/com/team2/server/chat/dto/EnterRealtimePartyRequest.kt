package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EnterRealtimePartyRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val nickname: String,
    val characterId: Long,
    val participantToken: String? = null,
)
