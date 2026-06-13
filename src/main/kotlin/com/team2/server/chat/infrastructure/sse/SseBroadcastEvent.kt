package com.team2.server.chat.infrastructure.sse

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter

data class SseBroadcastEvent(
    val partyId: Long,
    val event: Set<ResponseBodyEmitter.DataWithMediaType>,
    val excludeToken: String? = null,
)
