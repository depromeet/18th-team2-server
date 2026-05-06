package com.team2.server.rollingpaper.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "주최자용 롤링페이퍼 상세 조회 응답")
data class OwnerRollingPaperDetailResponse(
    @Schema(description = "롤링페이퍼 ID", example = "10")
    val rollingPaperId: Long,
    @Schema(description = "롤링페이퍼 내용", example = "생일 축하해요!")
    val content: String,
    @Schema(description = "롤링페이퍼 작성자 닉네임", example = "축하요정")
    val writerNickname: String,
    @Schema(description = "최신순 기준 현재 롤링페이퍼 순번. 1부터 시작합니다.", example = "1")
    val position: Long,
    @Schema(description = "파티의 전체 롤링페이퍼 수", example = "12")
    val totalCount: Long,
    @Schema(description = "최신순 화면 순서상 바로 이전 롤링페이퍼 ID. 첫 번째 롤링페이퍼면 null", example = "11")
    val previousRollingPaperId: Long?,
    @Schema(description = "최신순 화면 순서상 바로 다음 롤링페이퍼 ID. 마지막 롤링페이퍼면 null", example = "9")
    val nextRollingPaperId: Long?,
)
