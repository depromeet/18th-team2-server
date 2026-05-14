package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.UpcomingPartyResponse
import com.team2.server.party.api.dto.UpcomingRealtimeScheduleResponse
import com.team2.server.party.application.dto.UpcomingPartyResult
import com.team2.server.party.application.dto.UpcomingRealtimeScheduleResult
import com.team2.server.party.application.usecase.GetUpcomingPartiesUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class MePartyController(
    private val getUpcomingPartiesUseCase: GetUpcomingPartiesUseCase,
) : MePartyApi {
    @GetMapping("/upcoming-parties")
    override fun getUpcomingParties(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<List<UpcomingPartyResponse>> {
        val results = getUpcomingPartiesUseCase.getUpcomingParties(principal.userId)
        return ApiResponse.success(results.map { it.toResponse() })
    }

    private fun UpcomingPartyResult.toResponse(): UpcomingPartyResponse =
        UpcomingPartyResponse(
            partyId = partyId,
            inviteToken = inviteToken,
            partyOption = partyOption,
            celebrantNickname = celebrantNickname,
            partyStartedAt = partyStartedAt,
            partyEndedAt = partyEndedAt,
            isHost = isHost,
            rollingPaperWritten = rollingPaperWritten,
            hostRollingPaperOpenAt = hostRollingPaperOpenAt,
            realtimeSchedule = realtimeSchedule?.toResponse(),
        )

    private fun UpcomingRealtimeScheduleResult.toResponse(): UpcomingRealtimeScheduleResponse =
        UpcomingRealtimeScheduleResponse(
            enterableFrom = enterableFrom,
            liveStartAt = liveStartAt,
            liveEndAt = liveEndAt,
        )
}
