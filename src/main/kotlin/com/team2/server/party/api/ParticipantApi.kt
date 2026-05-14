package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.PartyParticipantsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface ParticipantApi {
    @Operation(
        summary = "파티 참여자 목록 조회",
        description = "실시간 파티 진행 기본화면용. 입장 순서로 정렬된 참여자 목록을 반환한다. RealtimeParty 전용, 참여자만 조회 가능.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "참여자 목록 조회 성공")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun getPartyParticipants(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<PartyParticipantsResponse>
}
