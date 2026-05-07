package com.team2.server.rollingpaper.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "롤링페이퍼 래퍼 조회 응답")
data class RollingPaperWrapperResponse(
    @Schema(description = "래퍼 ID. 롤링페이퍼 작성 요청의 wrapperId로 전달합니다.", example = "1")
    val wrapperId: Long,
    @Schema(description = "래퍼 이름", example = "Topping_Candle")
    val name: String,
    @Schema(description = "래퍼 이미지 URL", example = "/images/rolling-paper-wrappers/Topping_Candle.svg")
    val wrapperImageUrl: String?,
) {
    companion object {
        fun from(result: RollingPaperWrapperResult): RollingPaperWrapperResponse =
            RollingPaperWrapperResponse(
                wrapperId = result.wrapperId,
                name = result.name,
                wrapperImageUrl = result.wrapperImageUrl,
            )
    }
}
