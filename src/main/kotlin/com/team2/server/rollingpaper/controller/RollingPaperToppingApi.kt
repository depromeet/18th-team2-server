package com.team2.server.rollingpaper.controller

import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.common.web.swagger.OptionalAuth
import com.team2.server.rollingpaper.application.dto.RollingPaperToppingResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Rolling Paper Topping", description = "롤링페이퍼 토핑 API")
interface RollingPaperToppingApi {
    @Operation(
        summary = "롤링페이퍼 토핑 목록 조회",
        description = "롤링페이퍼 작성 시 선택 가능한 토핑 목록을 조회한다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
        ],
    )
    @OptionalAuth
    @SwaggerApiResponse(
        responseCode = "200",
        description = "토핑 목록 조회 성공",
    )
    @InternalServerErrorResponse
    fun getRollingPaperToppings(): ApiResponse<List<RollingPaperToppingResult>>
}
