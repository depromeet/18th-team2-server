package com.team2.server.party.api.dto

import com.team2.server.party.application.dto.ArchiveRole
import com.team2.server.party.domain.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "보관함 파티 상세 응답")
data class ArchivePartyDetailResponse(
    @Schema(description = "파티 ID")
    val partyId: Long,
    @Schema(description = "파티 주인공 닉네임. 없으면 null", nullable = true)
    val celebrantNickname: String?,
    @Schema(description = "REALTIME 또는 PAPER_ONLY")
    val partyOption: PartyOption,
    @Schema(description = "조회자 역할")
    val role: ArchiveRole,
    @Schema(description = "파티 시작 시각 (KST)")
    val partyStartedAt: LocalDateTime,
    @Schema(description = "파티 종료 시각 (KST) = startedAt + 7일")
    val partyEndedAt: LocalDateTime,
    @Schema(description = "RealtimeParticipantProfile 수. PAPER_ONLY는 0")
    val participantCount: Long,
    @Schema(description = "롤링페이퍼 총 개수")
    val paperCount: Long,
    @Schema(description = "참가자 닉네임 목록. PAPER_ONLY는 빈 배열")
    val participants: List<ArchiveParticipantResponse>,
    @Schema(description = "최근 50개 채팅 메시지 (createdAt DESC). PAPER_ONLY는 빈 배열")
    val chatMessages: List<ArchiveChatMessageResponse>,
    @Schema(description = "응답에 담지 못한 추가 메시지 존재 여부")
    val chatHasMore: Boolean,
    @Schema(description = "본인이 롤페를 작성했는지")
    val myPaperWritten: Boolean,
    @Schema(description = "본인 롤페 본문. 미작성이면 null")
    val myPaperContent: String?,
    @Schema(description = "본인 롤페 작성 시 닉네임 스냅샷. 미작성이면 null")
    val myPaperWriterNickname: String?,
    @Schema(description = "본인 롤페 토핑 이미지 URL. 미작성이면 null")
    val myPaperToppingImageUrl: String?,
)
