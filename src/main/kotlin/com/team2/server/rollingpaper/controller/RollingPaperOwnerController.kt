package com.team2.server.rollingpaper.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListResponse
import com.team2.server.rollingpaper.usecase.GetRollingPaperListUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class RollingPaperOwnerController(
    private val getRollingPaperListUseCase: GetRollingPaperListUseCase,
) : RollingPaperOwnerApi {
    @GetMapping("/{partyId}/rolling-papers")
    override fun getOwnerRollingPapers(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
        @RequestParam(defaultValue = "1") page: Int,
    ): ApiResponse<OwnerRollingPaperListResponse> =
        ApiResponse.success(getRollingPaperListUseCase.getOwnerList(partyId, principal.userId, page))
}
