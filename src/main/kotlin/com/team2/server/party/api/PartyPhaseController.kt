package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.usecase.AdvancePartyPhaseUseCase
import com.team2.server.party.application.usecase.GetPartyPhaseUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyPhaseController(
    private val getPartyPhaseUseCase: GetPartyPhaseUseCase,
    private val advancePartyPhaseUseCase: AdvancePartyPhaseUseCase,
) : PartyPhaseApi {
    @GetMapping("/{partyId}/phase")
    override fun getPhase(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
    ): ApiResponse<PartyPhaseResult> =
        ApiResponse.success(
            HttpStatus.OK,
            getPartyPhaseUseCase(partyId, principal?.userId, participantToken),
        )

    @PostMapping("/{partyId}/phase/advance")
    override fun advancePhase(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
        @RequestBody request: AdvancePartyPhaseRequest,
    ): ApiResponse<PartyPhaseResult> =
        ApiResponse.success(
            HttpStatus.OK,
            advancePartyPhaseUseCase(partyId, principal?.userId, participantToken, request.currentPhase),
        )
}
