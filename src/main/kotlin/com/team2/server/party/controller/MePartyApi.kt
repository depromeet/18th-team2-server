package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.party.dto.UpcomingPartyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface MePartyApi {
    @Operation(
        summary = "홈 다가오는 파티 목록 조회",
        description = "인증 회원이 참여 중이고 아직 종료되지 않은 파티 목록을 조회한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "다가오는 파티 목록 조회 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun getUpcomingParties(
        @Parameter(hidden = true) principal: UserPrincipal,
    ): ApiResponse<List<UpcomingPartyResponse>>
}
