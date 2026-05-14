package com.team2.server.me.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.me.api.dto.MeAccountResponse
import com.team2.server.me.application.usecase.GetMeAccountUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class MeAccountController(
    private val getMeAccountUseCase: GetMeAccountUseCase,
) {
    @GetMapping("/account")
    fun getAccount(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<MeAccountResponse> = ApiResponse.success(getMeAccountUseCase.invoke(principal.userId))
}
