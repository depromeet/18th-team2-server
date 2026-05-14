package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.application.usecase.GetArchivedPartyDetailUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/archive")
class ArchivePartyDetailController(
    private val getArchivedPartyDetailUseCase: GetArchivedPartyDetailUseCase,
) : ArchivePartyDetailApi {
    @GetMapping("/party/{partyId}")
    override fun getArchivedPartyDetail(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse> =
        ApiResponse.success(getArchivedPartyDetailUseCase.invoke(partyId, principal.userId))
}
