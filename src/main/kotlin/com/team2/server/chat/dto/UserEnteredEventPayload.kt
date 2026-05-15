package com.team2.server.chat.dto

import com.team2.server.chat.domain.vo.ParticipantRole

data class UserEnteredEventPayload(
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val role: ParticipantRole,
)
