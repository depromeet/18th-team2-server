package com.team2.server.rollingpaper.controller

import com.team2.server.common.web.ApiResponse
import com.team2.server.rollingpaper.dto.RollingPaperToppingResult
import com.team2.server.rollingpaper.usecase.GetRollingPaperToppingsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rolling-paper-toppings")
class RollingPaperToppingController(
    private val getRollingPaperToppingsUseCase: GetRollingPaperToppingsUseCase,
) : RollingPaperToppingApi {
    @GetMapping
    override fun getRollingPaperToppings(): ApiResponse<List<RollingPaperToppingResult>> =
        ApiResponse.success(getRollingPaperToppingsUseCase.getToppings())
}
