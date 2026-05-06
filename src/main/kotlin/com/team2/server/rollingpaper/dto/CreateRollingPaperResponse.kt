package com.team2.server.rollingpaper.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "롤링페이퍼 작성 응답")
data class CreateRollingPaperResponse(
    @Schema(description = "생성된 롤링페이퍼 ID", example = "10")
    val rollingPaperId: Long,
)
