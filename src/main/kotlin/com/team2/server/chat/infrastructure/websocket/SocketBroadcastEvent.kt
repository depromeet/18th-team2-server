package com.team2.server.chat.infrastructure.websocket

data class SocketEventMessage(
    val event: String,
    val data: Any,
)

data class SocketBroadcastEvent(
    val partyId: Long,
    val eventName: String,
    val payload: Any,
)
