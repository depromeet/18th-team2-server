package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.ActivateInviteLinkResponse
import com.team2.server.party.service.PartyInviteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Party", description = "파티 API")
@RestController
@RequestMapping("/api/v1/parties")
class PartyInviteController(
    private val partyInviteService: PartyInviteService,
) {
    @Operation(
        summary = "파티 초대링크 활성화",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @PostMapping("/{partyId}/invite-link")
    fun activateInviteLink(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<ActivateInviteLinkResponse> =
        ApiResponse.success(partyInviteService.activateInviteLink(partyId, principal.userId))
}
