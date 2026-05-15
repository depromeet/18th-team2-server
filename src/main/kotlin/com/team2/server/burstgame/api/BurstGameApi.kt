@file:Suppress("MaxLineLength")

package com.team2.server.burstgame.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.burstgame.api.dto.BurstGameSnapshotResponse
import com.team2.server.burstgame.api.dto.StartBurstGameResponse
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapRequest
import com.team2.server.burstgame.api.dto.SubmitBurstGameTapResponse
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
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Burst Game", description = "실시간 파티 박터뜨리기 API")
interface BurstGameApi {
    @Operation(
        summary = "박터뜨리기 시작",
        description = """
실시간 파티의 박터뜨리기 라운드를 시작합니다.

로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참여자는 `X-Participant-Token: {participantToken}` 헤더를 사용합니다.
이미 active 라운드가 있으면 기존 라운드 snapshot을 반환하고, 종료된 라운드가 TTL 안에 남아 있으면 재시작을 막습니다.
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
라운드 진행 중 터치 batch를 제출합니다.

`tapCount`는 1~30, `clientSequence`는 참가자별 batch 멱등성 키입니다.
중복 sequence와 종료 후 submit은 200 응답에서 `accepted=false`로 표현합니다.
""",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "터치 batch 처리 성공. 중복 sequence 또는 종료 후 submit도 accepted=false로 반환합니다.",
    )
    @ValidationErrorResponse
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "라운드 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "429",
        description = "rate limit 초과",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun submitTaps(
        @Parameter(description = "라운드 ID") roundId: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        request: SubmitBurstGameTapRequest,
    ): ApiResponse<SubmitBurstGameTapResponse>

    @Operation(
        summary = "박터뜨리기 snapshot 조회",
        description = "partyId 기준으로 active 또는 TTL 안의 ended 라운드 snapshot을 조회합니다.",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "snapshot 조회 성공. ended snapshot에서만 winners가 채워집니다.",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "라운드 없음",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @InternalServerErrorResponse
    fun getSnapshot(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): ApiResponse<BurstGameSnapshotResponse>
}
