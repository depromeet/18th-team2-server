package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.UpsertParticipantRealtimeProfileRequest
import com.team2.server.party.application.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.application.usecase.GetMyRealtimeProfileUseCase
import com.team2.server.party.application.usecase.UpsertMyRealtimeProfileUseCase
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class ParticipantRealtimeProfileController(
    private val getMyRealtimeProfileUseCase: GetMyRealtimeProfileUseCase,
    private val upsertMyRealtimeProfileUseCase: UpsertMyRealtimeProfileUseCase,
) : ParticipantRealtimeProfileApi {
    @GetMapping("/{inviteToken}/participants/me/realtime-profile")
    override fun getMyRealtimeProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
    ): ApiResponse<ParticipantRealtimeProfileResult> =
        ApiResponse.success(getMyRealtimeProfileUseCase.invoke(inviteToken, principal.userId))

    @PutMapping("/{inviteToken}/participants/me/realtime-profile")
    override fun upsertMyRealtimeProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
        @Valid @RequestBody request: UpsertParticipantRealtimeProfileRequest,
    ): ApiResponse<ParticipantRealtimeProfileResult> =
        ApiResponse.success(upsertMyRealtimeProfileUseCase.invoke(inviteToken, principal.userId, request))
}
