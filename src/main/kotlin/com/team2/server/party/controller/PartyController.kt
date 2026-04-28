package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.service.PartyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Party", description = "파티 API")
@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val partyService: PartyService,
) {
    @Operation(
        summary = "파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> = ApiResponse.success(partyService.createParty(principal.userId, request))
}
