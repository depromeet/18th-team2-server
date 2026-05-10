package com.team2.server.chat.dto

import com.team2.server.chat.entity.ChatMessage
import java.time.LocalDateTime

data class ChatMessageResponse(
    val messageId: Long,
    val content: String,
    val senderNickname: String,
    val senderCharacterId: Long?,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(message: ChatMessage): ChatMessageResponse =
            ChatMessageResponse(
                messageId = message.id,
                content = message.content,
                senderNickname = message.profile.nickname,
                senderCharacterId = message.profile.character?.id,
                sentAt = message.createdAt,
            )
    }
}
