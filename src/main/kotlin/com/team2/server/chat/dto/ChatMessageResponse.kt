package com.team2.server.chat.dto

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.entity.ChatMessage
import java.time.LocalDateTime

data class ChatMessageResponse(
    val messageId: Long,
    val content: String,
    val senderNickname: String,
    val senderCharacterId: Long?,
    val senderCharacterImageUrl: String?,
    val senderRole: ParticipantRole,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(
            message: ChatMessage,
            isCelebrant: Boolean,
            imageUrl: String?,
        ): ChatMessageResponse =
            ChatMessageResponse(
                messageId = message.id,
                content = message.content,
                senderNickname = message.profile.nickname,
                senderCharacterId = message.profile.character?.id,
                senderCharacterImageUrl = imageUrl,
                senderRole = if (isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
                sentAt = message.createdAt,
            )
    }
}
