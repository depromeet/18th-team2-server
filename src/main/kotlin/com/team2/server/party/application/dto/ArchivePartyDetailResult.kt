package com.team2.server.party.application.dto

import com.team2.server.chat.dto.ArchiveChatMessageItem
import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDateTime

data class ArchivePartyDetailResult(
    val partyId: Long,
    val partyName: String,
    val partyOption: PartyOption,
    val role: ArchiveRole,
    val partyStartedAt: LocalDateTime,
    val partyEndedAt: LocalDateTime,
    val participantCount: Long,
    val paperCount: Long,
    val participants: List<ArchiveParticipantItem>,
    val chatMessages: List<ArchiveChatMessageItem>,
    val chatHasMore: Boolean,
    val myPaperWritten: Boolean,
    val myPaperContent: String?,
    val myPaperWriterNickname: String?,
    val myPaperToppingImageUrl: String?,
)

data class ArchiveParticipantItem(
    val nickname: String,
)

enum class ArchiveRole {
    HOST,
    PARTICIPANT,
}
