package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.CreateRealtimePartyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface PartyApi {
    @Operation(
        summary = "실시간 파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "201", description = "파티 생성 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun createRealtimeParty(
        @Parameter(hidden = true) principal: UserPrincipal,
        request: CreateRealtimePartyRequest,
    ): ApiResponse<CreatePartyResponse>

    @Operation(
        summary = "롤링페이퍼 파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "201", description = "파티 생성 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun createPaperOnlyParty(
        @Parameter(hidden = true) principal: UserPrincipal,
        request: CreatePaperOnlyPartyRequest,
    ): ApiResponse<CreatePartyResponse>

    @Operation(
        summary = "파티 삭제",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "파티 삭제 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun deleteParty(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<Unit>
}
