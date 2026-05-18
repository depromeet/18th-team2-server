package com.team2.server.burstgame.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.burstgame.api.dto.BurstGameStateResponse
import com.team2.server.burstgame.api.dto.StartBurstGameResponse
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapRequest
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapResponse
import com.team2.server.burstgame.application.usecase.GetBurstGameSnapshotUseCase
import com.team2.server.burstgame.application.usecase.StartBurstGameUseCase
import com.team2.server.burstgame.application.usecase.SubmitBurstGameTapUseCase
import com.team2.server.common.web.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class BurstGameController(
    private val startBurstGameUseCase: StartBurstGameUseCase,
    private val submitBurstGameTapUseCase: SubmitBurstGameTapUseCase,
    private val getBurstGameSnapshotUseCase: GetBurstGameSnapshotUseCase,
) : BurstGameApi {
    @PostMapping("/api/v1/parties/{partyId}/burst-game/start")
    override fun start(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<StartBurstGameResponse> =
        ApiResponse.success(
            startBurstGameUseCase(partyId, principal?.userId, participantToken),
        )

    @PostMapping("/api/v1/burst-game/rounds/{roundId}/taps")
    override fun submitTaps(
        @PathVariable roundId: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @RequestBody @Valid request: SubmitBurstGameTapRequest,
    ): ApiResponse<SubmitBurstGameTapResponse> =
        ApiResponse.success(
            submitBurstGameTapUseCase(
                roundId = roundId,
                userId = principal?.userId,
                participantToken = participantToken,
                tapCount = request.tapCount,
                clientSequence = request.clientSequence,
            ),
        )

    @GetMapping("/api/v1/parties/{partyId}/burst-game")
    override fun getState(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<BurstGameStateResponse> =
        ApiResponse.success(
            getBurstGameSnapshotUseCase(partyId, principal?.userId, participantToken),
        )
}
