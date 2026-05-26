package com.team2.server.burstgame.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapRequest
import com.team2.server.burstgame.application.dto.BurstGameStateResponse
import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.dto.StartBurstGameResponse
import com.team2.server.burstgame.application.dto.SubmitBurstGameTapResponse
import com.team2.server.burstgame.application.usecase.BlowCandleUseCase
import com.team2.server.burstgame.application.usecase.GetBurstGameSnapshotUseCase
import com.team2.server.burstgame.application.usecase.GetCandleBlowStateUseCase
import com.team2.server.burstgame.application.usecase.StartBurstGameUseCase
import com.team2.server.burstgame.application.usecase.SubmitBurstGameTapUseCase
import com.team2.server.common.web.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
class BurstGameController(
    private val getCandleBlowStateUseCase: GetCandleBlowStateUseCase,
    private val blowCandleUseCase: BlowCandleUseCase,
    private val startBurstGameUseCase: StartBurstGameUseCase,
    private val submitBurstGameTapUseCase: SubmitBurstGameTapUseCase,
    private val getBurstGameSnapshotUseCase: GetBurstGameSnapshotUseCase,
) : BurstGameApi {
    @GetMapping("/api/v1/parties/{partyId}/candle-blow")
    override fun getCandleBlowState(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<CandleBlowResponse> =
        ApiResponse.success(
            getCandleBlowStateUseCase(partyId, principal?.userId, participantToken),
        )

    @PostMapping("/api/v1/parties/{partyId}/candle-blow/candles/{candleId}")
    override fun blowCandle(
        @PathVariable partyId: Long,
        @PathVariable candleId: Int,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<CandleBlowResponse> =
        ApiResponse.success(
            blowCandleUseCase(
                partyId = partyId,
                candleId = candleId,
                userId = principal?.userId,
                participantToken = participantToken,
            ),
        )

    @PostMapping("/api/v1/parties/{partyId}/burst-game/start")
    override fun start(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): ApiResponse<StartBurstGameResponse> =
        ApiResponse.success(
            startBurstGameUseCase(partyId, principal?.userId, participantToken),
        )

    @PostMapping("/api/v1/parties/{partyId}/burst-game/taps")
    override fun submitTaps(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @RequestBody @Valid request: SubmitBurstGameTapRequest,
    ): ApiResponse<SubmitBurstGameTapResponse> =
        ApiResponse.success(
            submitBurstGameTapUseCase(
                partyId = partyId,
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
