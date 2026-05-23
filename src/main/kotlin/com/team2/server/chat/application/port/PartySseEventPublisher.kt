package com.team2.server.chat.application.port

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter

interface PartySseEventPublisher {
    fun broadcastAfterCommit(
        partyId: Long,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
        excludeToken: String? = null,
    )
}
