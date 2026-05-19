package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.PartyParticipantsResponse
import com.team2.server.party.application.usecase.GetPartyParticipantsUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class ParticipantController(
    private val getPartyParticipantsUseCase: GetPartyParticipantsUseCase,
) : ParticipantApi {
    @GetMapping("/{partyId}/participants")
    override fun getPartyParticipants(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable partyId: Long,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<PartyParticipantsResponse> {
        val result =
            getPartyParticipantsUseCase.invoke(
                partyId = partyId,
                userId = principal?.userId,
                participantToken = participantToken,
            )
        return ApiResponse.success(HttpStatus.OK, PartyParticipantsResponse.from(result))
    }
}
