package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.domain.vo.PartyPhase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party Phase", description = "실시간 파티 Phase API")
interface PartyPhaseApi {
    @Operation(
        summary = "현재 Phase 조회",
        description = """
중간 입장 시 현재 Phase와 시작 시각, 서버 현재 시각 반환.

**인증**
로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참가자는 `X-Participant-Token: {participantToken}` 헤더를 사용한다. 둘 중 하나는 반드시 포함해야 한다.
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "현재 Phase 조회 성공")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun getPhase(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<PartyPhaseResult>

    @Operation(
        summary = "Phase 전환",
        description = """
ENTRY→MUSIC: 호스트 전용. MUSIC→CANDLE: 모든 파티 멤버. currentPhase가 이미 변경된 경우 현재 phase 그대로 반환.

**인증**
로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참가자는 `X-Participant-Token: {participantToken}` 헤더를 사용한다.
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "Phase 전환 성공 (CAS 불일치 시에도 200 반환)")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun advancePhase(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
        request: AdvancePartyPhaseRequest,
    ): ApiResponse<PartyPhaseResult>
}

data class AdvancePartyPhaseRequest(
    val currentPhase: PartyPhase,
)
