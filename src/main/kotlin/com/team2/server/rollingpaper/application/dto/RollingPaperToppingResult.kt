package com.team2.server.rollingpaper.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "롤링페이퍼 토핑 조회 응답")
data class RollingPaperToppingResult(
    @Schema(description = "토핑 ID. 롤링페이퍼 작성 요청의 toppingId로 전달합니다.", example = "1")
    val toppingId: Long,
    @Schema(description = "토핑 이름", example = "Topping_Candle")
    val name: String,
    @Schema(description = "토핑 이미지 URL", example = "/images/rolling-paper-wrappers/Topping_Candle.svg")
    val toppingImageUrl: String,
)
