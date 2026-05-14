package com.team2.server.rollingpaper.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.common.web.swagger.OptionalAuth
import com.team2.server.rollingpaper.dto.CreateRollingPaperRequest
import com.team2.server.rollingpaper.dto.CreateRollingPaperResponse
import com.team2.server.rollingpaper.dto.ParticipantRollingPaperListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Rolling Paper", description = "롤링페이퍼 API")
interface RollingPaperApi {
    @Operation(
        summary = "참가자용 롤링페이퍼 목록 조회",
        description =
            "초대 토큰으로 롤링페이퍼 목록과 상세 오버레이용 본문을 조회한다. 인증 없이도 조회 가능하다. " +
                "Authorization header를 보낼 경우 유효한 Bearer token이어야 한다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
        ],
    )
    @OptionalAuth
    @SwaggerApiResponse(
        responseCode = "200",
        description = "참가자용 롤링페이퍼 목록 조회 성공",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getParticipantRollingPapers(
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
        @Parameter(description = "페이지 번호. 1보다 작으면 1로 보정합니다.", example = "1") page: Int,
    ): ApiResponse<ParticipantRollingPaperListResponse>

    @Operation(
        summary = "롤링페이퍼 작성",
        description =
            "초대 토큰으로 롤링페이퍼를 작성한다. 인증 없이도 작성 가능하다. " +
                "Authorization header를 보낼 경우 유효한 Bearer token이어야 한다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
        ],
    )
    @OptionalAuth
    @SwaggerApiResponse(
        responseCode = "201",
        description = "롤링페이퍼 작성 성공",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "입력값 검증 실패, 만료된 초대 토큰 또는 종료된 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "입력값 검증 실패",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "VALIDATION_ERROR",
                                "message": "writerNickname: 닉네임은 필수입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "만료된 초대 토큰",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "INVITE_LINK_EXPIRED",
                                "message": "만료된 초대링크입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "종료된 파티",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_ENDED",
                                "message": "이미 종료된 파티입니다"
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
        description = "파티 또는 래퍼 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "래퍼 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "ROLLING_PAPER_WRAPPER_NOT_FOUND",
                                "message": "롤링페이퍼 래퍼를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "닉네임 중복 또는 이미 작성함",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "닉네임 중복",
                        value = """
                            {
                              "status": 409,
                              "error": {
                                "code": "ROLLING_PAPER_NICKNAME_DUPLICATED",
                                "message": "이미 사용 중인 롤링페이퍼 닉네임입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "이미 작성함",
                        value = """
                            {
                              "status": 409,
                              "error": {
                                "code": "ROLLING_PAPER_ALREADY_WRITTEN",
                                "message": "이미 롤링페이퍼를 작성했습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun createRollingPaper(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
        request: CreateRollingPaperRequest,
    ): ApiResponse<CreateRollingPaperResponse>
}
