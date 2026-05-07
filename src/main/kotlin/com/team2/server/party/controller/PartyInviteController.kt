package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.ActivateInviteLinkResponse
import com.team2.server.party.service.PartyInviteService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyInviteController(
    private val partyInviteService: PartyInviteService,
) : PartyInviteApi {
    @PostMapping("/{partyId}/invite-link")
    override fun activateInviteLink(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<ActivateInviteLinkResponse> =
        ApiResponse.success(partyInviteService.activateInviteLink(partyId, principal.userId))
}
