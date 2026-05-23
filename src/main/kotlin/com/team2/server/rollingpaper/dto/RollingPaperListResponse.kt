package com.team2.server.rollingpaper.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.domain.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "참가자용 롤링페이퍼 목록 조회 응답")
data class ParticipantRollingPaperListResponse(
    @Schema(
        description = "파티 옵션. REALTIME: 실시간 파티, PAPER_ONLY: 롤링페이퍼 전용 파티",
        allowableValues = ["REALTIME", "PAPER_ONLY"],
        example = "REALTIME",
    )
    val partyOption: PartyOption,
    @Schema(
        description = "실시간 파티 종료 기준 시각. 이 시각 이후 참가자는 롤링페이퍼 목록을 볼 수 있습니다. PAPER_ONLY면 null",
        nullable = true,
        example = "2026-05-05T22:10:00",
    )
    val liveEndAt: LocalDateTime?,
    @Schema(description = "롤링페이퍼 목록")
    val items: List<ParticipantRollingPaperListItemResult>,
    @Schema(description = "페이지네이션 정보")
    val pageInfo: RollingPaperPageInfoResult,
) {
    companion object {
        fun from(result: ParticipantRollingPaperListResult): ParticipantRollingPaperListResponse =
            ParticipantRollingPaperListResponse(
                partyOption = result.partyOption,
                liveEndAt = result.liveEndAt,
                items = result.items,
                pageInfo = result.pageInfo,
            )
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "주최자용 롤링페이퍼 목록 조회 응답")
data class OwnerRollingPaperListResponse(
    @Schema(description = "파티 주인공 이름", example = "홍길동", nullable = true)
    val celebrantNickname: String?,
    @Schema(description = "파티 자체 종료 시각", example = "2026-05-12T14:30:00")
    val partyEndAt: LocalDateTime,
    @Schema(description = "롤링페이퍼 목록")
    val items: List<OwnerRollingPaperListItemResult>,
    @Schema(description = "페이지네이션 정보")
    val pageInfo: RollingPaperPageInfoResult,
) {
    companion object {
        fun from(result: OwnerRollingPaperListResult): OwnerRollingPaperListResponse =
            OwnerRollingPaperListResponse(
                celebrantNickname = result.celebrantNickname,
                partyEndAt = result.partyEndAt,
                items = result.items,
                pageInfo = result.pageInfo,
            )
    }
}
