package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ArchiveChatMessageItem
import com.team2.server.chat.dto.ArchiveChatSectionResult
import com.team2.server.chat.repository.ChatMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetArchiveChatSectionUseCase(
    private val chatMessageRepository: ChatMessageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(partyId: Long): ArchiveChatSectionResult {
        val total = chatMessageRepository.countByPartyId(partyId)
        val recent =
            chatMessageRepository.findRecentByPartyId(partyId, PageRequest.of(0, CHAT_RECENT_LIMIT))
        val items =
            recent.map { msg ->
                ArchiveChatMessageItem(
                    id = msg.id,
                    authorName = msg.profile.nickname,
                    content = msg.content,
                    sentAt = msg.createdAt,
                )
            }
        return ArchiveChatSectionResult(messages = items, hasMore = total > items.size)
    }

    companion object {
        const val CHAT_RECENT_LIMIT: Int = 50
    }
}
