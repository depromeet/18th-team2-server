package com.team2.server.chat.dto

data class EnterRealtimePartyResponse(
    val participantToken: String,
    val messages: List<ChatMessageResponse>,
)
