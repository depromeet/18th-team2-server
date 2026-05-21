package com.team2.server.chat.dto

import java.time.LocalDateTime

data class ArchiveChatSectionResult(
    val messages: List<ArchiveChatMessageItem>,
    val hasMore: Boolean,
)

data class ArchiveChatMessageItem(
    val id: Long,
    val authorName: String,
    val content: String,
    val sentAt: LocalDateTime,
)
