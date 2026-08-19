package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendChatMessageSocketRequest(
    // REST 경로의 SendChatMessageRequest 와 동일한 제약을 유지한다.
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,
    @field:NotBlank
    val participantToken: String,
    @field:NotBlank
    val clientRequestId: String,
)
