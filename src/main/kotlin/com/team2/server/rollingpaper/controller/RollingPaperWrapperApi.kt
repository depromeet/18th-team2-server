package com.team2.server.rollingpaper.controller

import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.rollingpaper.dto.RollingPaperWrapperResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Rolling Paper Wrapper", description = "롤링페이퍼 래퍼 API")
interface RollingPaperWrapperApi {
    @Operation(
        summary = "롤링페이퍼 래퍼 목록 조회",
        description = "롤링페이퍼 작성 시 선택 가능한 래퍼 목록을 조회한다.",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "래퍼 목록 조회 성공",
    )
    @InternalServerErrorResponse
    fun getRollingPaperWrappers(): ApiResponse<List<RollingPaperWrapperResponse>>
}
