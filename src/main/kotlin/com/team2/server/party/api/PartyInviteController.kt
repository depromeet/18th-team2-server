package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.ActivateInviteLinkResponse
import com.team2.server.party.application.usecase.ActivateInviteLinkUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyInviteController(
    private val activateInviteLinkUseCase: ActivateInviteLinkUseCase,
) : PartyInviteApi {
    @PostMapping("/{partyId}/invite-link")
    override fun activateInviteLink(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<ActivateInviteLinkResponse> {
        val token = activateInviteLinkUseCase.activate(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(ActivateInviteLinkResponse(token = token))
    }
}
