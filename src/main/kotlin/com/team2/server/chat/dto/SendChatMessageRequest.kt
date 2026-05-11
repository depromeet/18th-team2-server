package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendChatMessageRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,
)
