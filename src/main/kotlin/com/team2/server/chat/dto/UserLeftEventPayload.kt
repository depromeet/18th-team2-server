package com.team2.server.chat.dto

import com.team2.server.chat.domain.vo.ParticipantRole

data class UserLeftEventPayload(
    val nickname: String,
    val role: ParticipantRole,
)
