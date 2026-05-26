package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.domain.vo.PartyPhase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Party Phase", description = "실시간 파티 Phase API")
interface PartyPhaseApi {
    @Operation(summary = "현재 Phase 조회", description = "중간 입장 시 현재 Phase와 시작 시각, 서버 현재 시각 반환")
    fun getPhase(
        principal: UserPrincipal?,
        participantToken: String?,
        partyId: Long,
    ): ApiResponse<PartyPhaseResult>

    @Operation(
        summary = "Phase 전환",
        description = "ENTRY→MUSIC: 호스트 전용. MUSIC→CANDLE: 모든 파티 멤버. currentPhase가 이미 변경된 경우 현재 phase 그대로 반환.",
    )
    fun advancePhase(
        principal: UserPrincipal?,
        participantToken: String?,
        partyId: Long,
        request: AdvancePartyPhaseRequest,
    ): ApiResponse<PartyPhaseResult>
}

data class AdvancePartyPhaseRequest(
    val currentPhase: PartyPhase,
)
