package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.CreateRealtimePartyRequest
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
    @PostMapping("/realtime")
    override fun createRealtimeParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateRealtimePartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(HttpStatus.CREATED, partyService.createRealtimeParty(principal.userId, request))

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/paper-only")
    override fun createPaperOnlyParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePaperOnlyPartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(HttpStatus.CREATED, partyService.createPaperOnlyParty(principal.userId, request))

    @PostMapping("/{partyType}")
    fun createPartyUnknownType(): Nothing = throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

    @DeleteMapping("/{partyId}")
    override fun deleteParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<Unit> {
        partyService.deleteParty(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(HttpStatus.OK, Unit)
    }
}
