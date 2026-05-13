package com.team2.server.rollingpaper.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperDetailResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListResponse
import com.team2.server.rollingpaper.usecase.GetRollingPaperDetailUseCase
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
    private val getRollingPaperDetailUseCase: GetRollingPaperDetailUseCase,
) : RollingPaperOwnerApi {
    @GetMapping("/{partyId}/rolling-papers")
    override fun getOwnerRollingPapers(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
        @RequestParam(defaultValue = "1") page: Int,
    ): ApiResponse<OwnerRollingPaperListResponse> =
        ApiResponse.success(getRollingPaperListUseCase.getOwnerList(partyId, principal.userId, page))

    @GetMapping("/{partyId}/rolling-papers/{rollingPaperId}")
    override fun getOwnerRollingPaperDetail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
        @PathVariable rollingPaperId: Long,
    ): ApiResponse<OwnerRollingPaperDetailResponse> =
        ApiResponse.success(getRollingPaperDetailUseCase.getOwnerDetail(partyId, rollingPaperId, principal.userId))
}
