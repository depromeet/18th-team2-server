package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.JoinPartyRequest
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.service.PartyParticipationService
import com.team2.server.party.service.PartyService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val partyService: PartyService,
    private val partyParticipationService: PartyParticipationService,
) : PartyApi {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{partyOption}")
    override fun createParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyOption: PartyOption,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(HttpStatus.CREATED, partyService.createParty(principal.userId, request, partyOption))

    @GetMapping("/{inviteToken}")
    override fun getPartyInfo(
        @PathVariable inviteToken: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<PartyInfoResponse> = ApiResponse.success(partyService.getPartyInfo(inviteToken, principal?.userId))

    @PostMapping("/{inviteToken}/participants")
    override fun joinParty(
        @PathVariable inviteToken: String,
        @Valid @RequestBody request: JoinPartyRequest,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<ParticipantResponse> =
        ApiResponse.success(
            partyParticipationService.joinParty(
                inviteToken,
                principal?.userId,
                request.nickname,
                request.characterId,
            ),
        )
}
