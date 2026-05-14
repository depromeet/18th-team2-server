package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.PartyInviteLookupResponse
import com.team2.server.party.api.dto.PartyInviteParticipationResponse
import com.team2.server.party.api.dto.RealtimeSchedule
import com.team2.server.party.application.dto.PartyInviteLookupResult
import com.team2.server.party.application.dto.RealtimeScheduleResult
import com.team2.server.party.application.usecase.JoinPartyInviteUseCase
import com.team2.server.party.application.usecase.LookupPartyInviteUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class PartyInviteLookupController(
    private val lookupPartyInviteUseCase: LookupPartyInviteUseCase,
    private val joinPartyInviteUseCase: JoinPartyInviteUseCase,
) : PartyInviteLookupApi {
    @GetMapping("/{inviteToken}")
    override fun getPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteLookupResponse> {
        val result = lookupPartyInviteUseCase.lookup(inviteToken, principal?.userId)
        return ApiResponse.success(result.toResponse())
    }

    @PostMapping("/{inviteToken}/participants/me")
    override fun joinPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteParticipationResponse> {
        val participantId = joinPartyInviteUseCase.join(inviteToken, principal.userId)
        return ApiResponse.success(PartyInviteParticipationResponse(participantId = participantId))
    }

    private fun PartyInviteLookupResult.toResponse(): PartyInviteLookupResponse =
        PartyInviteLookupResponse(
            partyId = partyId,
            celebrantNickname = celebrantNickname,
            isHost = isHost,
            partyOption = partyOption,
            partyEnded = partyEnded,
            rollingPaperWritten = rollingPaperWritten,
            partyStartDate = partyStartDate,
            partyEndDate = partyEndDate,
            realtimeSchedule = realtimeSchedule?.toResponse(),
        )

    private fun RealtimeScheduleResult.toResponse(): RealtimeSchedule =
        RealtimeSchedule(
            liveStartAt = liveStartAt,
            enterableFrom = enterableFrom,
            liveEndAt = liveEndAt,
            liveDurationMinutes = liveDurationMinutes,
        )
}
