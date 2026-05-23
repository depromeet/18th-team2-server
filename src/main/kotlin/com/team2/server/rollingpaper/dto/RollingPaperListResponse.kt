package com.team2.server.rollingpaper.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.rollingpaper.application.dto.OwnerRollingPaperListItemResult
import com.team2.server.rollingpaper.application.dto.OwnerRollingPaperListResult
import com.team2.server.rollingpaper.application.dto.ParticipantRollingPaperListItemResult
import com.team2.server.rollingpaper.application.dto.ParticipantRollingPaperListResult
import com.team2.server.rollingpaper.application.dto.RollingPaperPageInfoResult
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
    @Schema(description = "실시간 파티 종료 시각. PAPER_ONLY면 null", nullable = true, example = "2026-05-05T22:10:00")
    val liveEndAt: LocalDateTime?,
    @Schema(description = "롤링페이퍼 목록")
    val items: List<ParticipantRollingPaperListItemResponse>,
    @Schema(description = "페이지네이션 정보")
    val pageInfo: RollingPaperPageInfoResponse,
) {
    companion object {
        fun from(result: ParticipantRollingPaperListResult): ParticipantRollingPaperListResponse =
            ParticipantRollingPaperListResponse(
                partyOption = result.partyOption,
                liveEndAt = result.liveEndAt,
                items = result.items.map(ParticipantRollingPaperListItemResponse::from),
                pageInfo = RollingPaperPageInfoResponse.from(result.pageInfo),
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
    val items: List<OwnerRollingPaperListItemResponse>,
    @Schema(description = "페이지네이션 정보")
    val pageInfo: RollingPaperPageInfoResponse,
) {
    companion object {
        fun from(result: OwnerRollingPaperListResult): OwnerRollingPaperListResponse =
            OwnerRollingPaperListResponse(
                celebrantNickname = result.celebrantNickname,
                partyEndAt = result.partyEndAt,
                items = result.items.map(OwnerRollingPaperListItemResponse::from),
                pageInfo = RollingPaperPageInfoResponse.from(result.pageInfo),
            )
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "롤링페이퍼 목록 페이지네이션 정보")
data class RollingPaperPageInfoResponse(
    @Schema(description = "현재 페이지. page가 1보다 작으면 1로 보정합니다.", example = "1")
    val page: Int,
    @Schema(description = "전체 롤링페이퍼 수", example = "12")
    val totalCount: Long,
    @Schema(description = "전체 페이지 수. 롤링페이퍼가 없으면 0입니다.", example = "2")
    val totalPages: Int,
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: RollingPaperPageInfoResult): RollingPaperPageInfoResponse =
            RollingPaperPageInfoResponse(
                page = result.page,
                totalCount = result.totalCount,
                totalPages = result.totalPages,
                hasNext = result.hasNext,
            )
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "참가자용 롤링페이퍼 목록 item")
data class ParticipantRollingPaperListItemResponse(
    @Schema(description = "롤링페이퍼 ID", example = "10")
    val rollingPaperId: Long,
    @Schema(description = "롤링페이퍼 작성자 닉네임", example = "축하요정")
    val writerNickname: String,
    @Schema(
        description = "롤링페이퍼 토핑 이미지 URL.",
        example = "/images/rolling-paper-wrappers/Topping_Candle.svg",
    )
    val toppingImageUrl: String,
) {
    companion object {
        fun from(result: ParticipantRollingPaperListItemResult): ParticipantRollingPaperListItemResponse =
            ParticipantRollingPaperListItemResponse(
                rollingPaperId = result.rollingPaperId,
                writerNickname = result.writerNickname,
                toppingImageUrl = result.toppingImageUrl,
            )
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "주최자용 롤링페이퍼 목록 item")
data class OwnerRollingPaperListItemResponse(
    @Schema(description = "롤링페이퍼 ID", example = "10")
    val rollingPaperId: Long,
    @Schema(description = "최신순 기준 현재 롤링페이퍼 순번. 1부터 시작합니다.", example = "1")
    val position: Long,
    @Schema(description = "롤링페이퍼 작성자 닉네임", example = "축하요정")
    val writerNickname: String,
    @Schema(description = "롤링페이퍼 내용. 최대 100자입니다.", example = "생일 축하해요!")
    val content: String,
    @Schema(
        description = "롤링페이퍼 토핑 이미지 URL.",
        example = "/images/rolling-paper-wrappers/Topping_Candle.svg",
    )
    val toppingImageUrl: String,
) {
    companion object {
        fun from(result: OwnerRollingPaperListItemResult): OwnerRollingPaperListItemResponse =
            OwnerRollingPaperListItemResponse(
                rollingPaperId = result.rollingPaperId,
                position = result.position,
                writerNickname = result.writerNickname,
                content = result.content,
                toppingImageUrl = result.toppingImageUrl,
            )
    }
}
