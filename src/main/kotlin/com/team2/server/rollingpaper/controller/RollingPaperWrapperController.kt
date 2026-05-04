package com.team2.server.rollingpaper.controller

import com.team2.server.common.response.ApiResponse
import com.team2.server.rollingpaper.dto.RollingPaperWrapperResponse
import com.team2.server.rollingpaper.usecase.GetRollingPaperWrappersUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rolling-paper-wrappers")
class RollingPaperWrapperController(
    private val getRollingPaperWrappersUseCase: GetRollingPaperWrappersUseCase,
) : RollingPaperWrapperApi {
    @GetMapping
    override fun getRollingPaperWrappers(): ApiResponse<List<RollingPaperWrapperResponse>> =
        ApiResponse.success(getRollingPaperWrappersUseCase.getWrappers().map(RollingPaperWrapperResponse::from))
}
