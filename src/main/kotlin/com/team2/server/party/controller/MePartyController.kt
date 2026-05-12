package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.dto.UpcomingPartyResponse
import com.team2.server.party.application.usecase.GetUpcomingPartiesUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class MePartyController(
    private val getUpcomingPartiesUseCase: GetUpcomingPartiesUseCase,
) : MePartyApi {
    @GetMapping("/upcoming-parties")
    override fun getUpcomingParties(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<List<UpcomingPartyResponse>> =
        ApiResponse.success(getUpcomingPartiesUseCase.getUpcomingParties(principal.userId))
}
