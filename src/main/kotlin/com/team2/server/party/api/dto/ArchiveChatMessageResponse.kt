package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "보관함 파티 상세 - 채팅 메시지 항목")
data class ArchiveChatMessageResponse(
    @Schema(description = "메시지 ID")
    val id: Long,
    @Schema(description = "작성자 닉네임 (RealtimeParticipantProfile.nickname)")
    val authorName: String,
    @Schema(description = "메시지 본문")
    val content: String,
    @Schema(description = "전송 시각 (KST)")
    val sentAt: LocalDateTime,
)
