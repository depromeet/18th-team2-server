package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.dto.PartyInviteLookupResponse
import com.team2.server.party.dto.PartyInviteParticipationResponse
import com.team2.server.party.application.usecase.JoinPartyInviteUseCase
import com.team2.server.party.application.usecase.LookupPartyInviteUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class PartyInviteLookupController(
    private val lookupPartyInviteUseCase: LookupPartyInviteUseCase,
    private val joinPartyInviteUseCase: JoinPartyInviteUseCase,
) : PartyInviteLookupApi {
    @GetMapping("/{inviteToken}")
    override fun getPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteLookupResponse> =
        ApiResponse.success(lookupPartyInviteUseCase.lookup(inviteToken, principal?.userId))

    @PostMapping("/{inviteToken}/participants/me")
    override fun joinPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteParticipationResponse> =
        ApiResponse.success(joinPartyInviteUseCase.join(inviteToken, principal.userId))
}
