package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.api.dto.CreatePartyResponse
import com.team2.server.party.api.dto.CreateRealtimePartyRequest
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.dto.RealtimePartyEndStatusResult
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.dto.RealtimePartyStateResult
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

    @Operation(
        summary = "주최자 실시간 파티 종료 상태 조회",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "종료 상태 조회 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun getRealtimeEndStatus(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<RealtimePartyEndStatusResult>

    @Operation(
        summary = "주최자 실시간 파티 종료 요청",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "종료 카운트다운 시작 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun startRealtimeEnd(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<RealtimePartyEndResult>

    @Operation(summary = "실시간 파티 상태 복구 조회")
    @SwaggerApiResponse(responseCode = "200", description = "상태 조회 성공")
    @InternalServerErrorResponse
    fun getRealtimeState(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(hidden = true) participantToken: String?,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<RealtimePartyStateResult>

    @Operation(summary = "실시간 파티 종료 후 다음 행동 조회")
    @SwaggerApiResponse(responseCode = "200", description = "다음 행동 조회 성공")
    @InternalServerErrorResponse
    fun getRealtimeNextAction(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(hidden = true) participantToken: String?,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<RealtimePartyNextActionResult>
}
