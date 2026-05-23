package com.team2.server.rollingpaper.dto

import com.team2.server.party.domain.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class ParticipantRollingPaperListResult(
    val partyOption: PartyOption,
    val liveEndAt: LocalDateTime?,
    val items: List<ParticipantRollingPaperListItemResult>,
    val pageInfo: RollingPaperPageInfoResult,
)

data class OwnerRollingPaperListResult(
    val celebrantNickname: String?,
    val partyEndAt: LocalDateTime,
    val items: List<OwnerRollingPaperListItemResult>,
    val pageInfo: RollingPaperPageInfoResult,
)

@Schema(description = "롤링페이퍼 목록 페이지네이션 정보")
data class RollingPaperPageInfoResult(
    @Schema(description = "현재 페이지. page가 1보다 작으면 1로 보정합니다.", example = "1")
    val page: Int,
    @Schema(description = "전체 롤링페이퍼 수", example = "12")
    val totalCount: Long,
    @Schema(description = "전체 페이지 수. 롤링페이퍼가 없으면 0입니다.", example = "2")
    val totalPages: Int,
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
)

@Schema(description = "참가자용 롤링페이퍼 목록 item")
data class ParticipantRollingPaperListItemResult(
    @Schema(description = "롤링페이퍼 ID", example = "10")
    val rollingPaperId: Long,
    @Schema(description = "롤링페이퍼 작성자 닉네임", example = "축하요정")
    val writerNickname: String,
    @Schema(
        description = "롤링페이퍼 토핑 이미지 URL.",
        example = "/images/rolling-paper-wrappers/Topping_Candle.svg",
    )
    val toppingImageUrl: String,
)

@Schema(description = "주최자용 롤링페이퍼 목록 item")
data class OwnerRollingPaperListItemResult(
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
)
