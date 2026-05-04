package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.service.PartyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
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
) : PartyApi {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{partyOption}")
    override fun createParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyOption: PartyOption,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(HttpStatus.CREATED, partyService.createParty(principal.userId, request, partyOption))

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{partyId}")
    override fun deleteParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<Unit> {
        partyService.deleteParty(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(HttpStatus.NO_CONTENT, Unit)
    }
}
