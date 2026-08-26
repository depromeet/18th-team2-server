package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EnterRealtimePartySocketRequest(
    // REST 경로의 EnterRealtimePartyRequest 와 동일한 제약을 유지한다.
    @field:NotBlank
    @field:Size(max = 20)
    val nickname: String,
    val characterId: Long,
    val participantToken: String? = null,
    val clientRequestId: String,
)
