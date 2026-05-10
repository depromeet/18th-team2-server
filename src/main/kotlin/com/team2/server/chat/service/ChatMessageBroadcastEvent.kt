package com.team2.server.chat.service

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter

data class ChatMessageBroadcastEvent(
    val partyId: Long,
    val event: Set<ResponseBodyEmitter.DataWithMediaType>,
)
