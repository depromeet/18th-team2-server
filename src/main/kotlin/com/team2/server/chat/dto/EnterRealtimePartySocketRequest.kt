package com.team2.server.chat.dto

data class EnterRealtimePartySocketRequest(
    val nickname: String,
    val characterId: Long,
    val participantToken: String? = null,
    val clientRequestId: String,
)
