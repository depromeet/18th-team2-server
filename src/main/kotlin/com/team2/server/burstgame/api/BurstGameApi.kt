@file:Suppress("MaxLineLength")

package com.team2.server.burstgame.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapRequest
import com.team2.server.burstgame.application.dto.BurstGameStateResponse
import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.dto.StartBurstGameResponse
import com.team2.server.burstgame.application.dto.SubmitBurstGameTapResponse
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.common.web.swagger.ValidationErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

private const val MIN_CANDLE_ID = 1L
private const val MAX_CANDLE_ID = 9L

@Tag(name = "Burst Game", description = "실시간 파티 박터뜨리기 API")
interface BurstGameApi {
    @Operation(
        summary = "촛불끄기 상태 조회",
        description = """
실시간 파티의 촛불끄기 상태를 조회합니다.

로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참여자는 `X-Participant-Token: {participantToken}` 헤더를 사용합니다.
시작 전에는 WAITING, 진행 중에는 ACTIVE, 모두 꺼졌거나 타임아웃되면 FINISHED를 반환합니다.
""",
    )
    @SwaggerApiResponse(responseCode = "200", description = "촛불끄기 상태 조회 성공")
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun getCandleBlowState(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): ApiResponse<CandleBlowResponse>

    @Operation(
        summary = "촛불 끄기",
        description = """
실시간 파티의 공유 촛불 1개를 끕니다.

`candleId`는 1부터 9까지 허용합니다.
이미 꺼진 촛불이거나 촛불끄기가 종료된 뒤의 요청도 실패가 아니라 200 OK와 현재 상태로 응답합니다.
""",
    )
    @SwaggerApiResponse(responseCode = "200", description = "촛불 끄기 처리 성공")
    @ValidationErrorResponse
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "시작 전 요청 또는 잘못된 candleId",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun blowCandle(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(description = "촛불 번호") @Min(MIN_CANDLE_ID) @Max(MAX_CANDLE_ID) candleId: Int,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): ApiResponse<CandleBlowResponse>

    @Operation(
        summary = "박터뜨리기 시작",
        description = """
실시간 파티의 박터뜨리기 라운드를 시작합니다.

로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참여자는 `X-Participant-Token: {participantToken}` 헤더를 사용합니다.
요청 시점부터 5초 뒤를 실제 시작 시각으로 정하고, 실제 시작 시각부터 20초 동안 터치를 집계합니다.
이미 active 라운드가 있으면 현재 상태를 반환하고, 종료된 라운드가 TTL 안에 남아 있으면 재시작을 막습니다.
""",
    )
    @SwaggerApiResponse(responseCode = "200", description = "라운드 시작 또는 active 라운드 조회 성공")
    @ValidationErrorResponse
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "이미 종료된 라운드",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun start(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): ApiResponse<StartBurstGameResponse>

    @Operation(
        summary = "박터뜨리기 터치 batch 제출",
        description = """
파티의 진행 중인 박터뜨리기 라운드에 터치 batch를 제출합니다.

`tapCount`는 1~30, `clientSequence`는 참가자별 batch 멱등성 키입니다.
카운트다운 중 요청, 중복 sequence, 종료 후 submit은 200 응답에서 `accepted=false`로 표현합니다.
""",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "터치 batch 처리 성공. 카운트다운 중 요청, 중복 sequence, 종료 후 submit도 accepted=false로 반환합니다.",
    )
    @ValidationErrorResponse
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티에 진행 중이거나 TTL 안에 남은 라운드 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "429",
        description = "rate limit 초과",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun submitTaps(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        @Valid request: SubmitBurstGameTapRequest,
    ): ApiResponse<SubmitBurstGameTapResponse>

    @Operation(
        summary = "박터뜨리기 상태 및 결과 조회",
        description = """
partyId 기준으로 진행 중인 라운드의 현재 상태 또는 TTL 안에 남아 있는 종료 결과를 조회합니다.
카운트다운 중에도 미래의 startedAt과 실제 플레이 시간 기준 remainingSeconds를 반환합니다.
현재 전체 터치 수를 totalTapCount로 반환하며, 진행 중에는 상위 3명 rankings를, 종료 후에는 1회 이상 터치한 참가자 전체 rankings를 반환합니다.
""",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "상태 및 결과 조회 성공. 진행 중 rankings는 상위 3명, 종료 rankings는 1회 이상 터치한 참가자 전체 순위입니다.",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "라운드 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun getState(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): ApiResponse<BurstGameStateResponse>
}
