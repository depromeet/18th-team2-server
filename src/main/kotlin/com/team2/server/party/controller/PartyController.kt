package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.JoinPartyRequest
import com.team2.server.party.dto.JoinPartyResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.service.PartyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Party", description = "파티 참여 API")
@RestController
@RequestMapping("/api/parties")
class PartyController(
    private val partyService: PartyService,
) {
    @Operation(summary = "파티 정보 조회", description = "shareLink로 파티 정보를 조회한다. 종료된 파티도 조회 가능.")
    @GetMapping("/{shareLink}")
    fun getPartyInfo(
        @PathVariable shareLink: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<PartyInfoResponse> {
        val result = partyService.getPartyInfo(shareLink, principal?.userId)
        return ApiResponse.success(result)
    }

    @Operation(summary = "파티 참여", description = "shareLink로 파티에 참여한다. 회원/비회원 모두 가능.")
    @PostMapping("/{shareLink}/participants")
    fun joinParty(
        @PathVariable shareLink: String,
        @Valid @RequestBody request: JoinPartyRequest,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<JoinPartyResponse> {
        val result = partyService.joinParty(shareLink, principal?.userId, request.nickname, request.characterId)
        return ApiResponse.success(result)
    }
}
