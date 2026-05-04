package com.team2.server.rollingpaper.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.rollingpaper.dto.CreateRollingPaperRequest
import com.team2.server.rollingpaper.dto.CreateRollingPaperResponse
import com.team2.server.rollingpaper.usecase.CreateRollingPaperUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class RollingPaperController(
    private val createRollingPaperUseCase: CreateRollingPaperUseCase,
) : RollingPaperApi {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{inviteToken}/rolling-papers")
    override fun createRollingPaper(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable inviteToken: String,
        @Valid @RequestBody request: CreateRollingPaperRequest,
    ): ApiResponse<CreateRollingPaperResponse> =
        ApiResponse.success(
            HttpStatus.CREATED,
            createRollingPaperUseCase.create(inviteToken, principal?.userId, request),
        )
}
