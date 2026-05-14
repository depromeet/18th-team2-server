package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.ParticipantRealtimeProfileResponse
import com.team2.server.party.api.dto.UpsertParticipantRealtimeProfileRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party Invite", description = "파티 초대장 API")
interface ParticipantRealtimeProfileApi {
    @Operation(
        summary = "실시간 파티 입장 프로필 조회",
        description =
            "회원이 실시간 파티에 입장하기 전에 자신의 닉네임/캐릭터 상태를 조회한다. " +
                "회원 participant가 없으면 생성한다. 만료된 토큰 또는 종료된 파티에는 실패한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "프로필 조회 성공")
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "초대 링크 만료, 파티 종료, 비-실시간 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "비-실시간 파티",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_NOT_REALTIME",
                                "message": "실시간 파티가 아닙니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 초대 토큰",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getMyRealtimeProfile(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
    ): ApiResponse<ParticipantRealtimeProfileResponse>

    @Operation(
        summary = "실시간 파티 입장 프로필 작성·수정",
        description =
            "회원의 실시간 파티 입장 프로필을 작성하거나 수정한다. 주최자는 닉네임을 변경할 수 없다 " +
                "(같은 값 재전송은 허용). 캐릭터는 모든 참가자가 변경 가능하다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "프로필 작성·수정 성공")
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "검증 실패, 초대 링크 만료, 파티 종료, 비-실시간 파티, 주최자 닉네임 변경 시도",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "주최자 닉네임 변경 불가",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_HOST_NICKNAME_NOT_EDITABLE",
                                "message": "주최자 닉네임은 변경할 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 초대 토큰 또는 캐릭터",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @InternalServerErrorResponse
    fun upsertMyRealtimeProfile(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
        request: UpsertParticipantRealtimeProfileRequest,
    ): ApiResponse<ParticipantRealtimeProfileResponse>
}
