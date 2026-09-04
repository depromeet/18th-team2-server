package com.team2.server.chat.infrastructure.websocket

data class SocketEventMessage(
    val event: String,
    val data: Any,
)

data class SocketErrorPayload(
    val code: String,
    val message: String,
)

data class SocketBroadcastEvent(
    val partyId: Long,
    val eventName: String,
    val payload: Any,
)

data class SocketPersonalEvent(
    val partyId: Long,
    val clientRequestId: String,
    val eventName: String,
    val payload: Any,
)
