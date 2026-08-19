package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Talk Calendar", description = "카카오 톡캘린더 연동 API")
interface TalkCalendarApi {
    @Operation(
        summary = "파티 일정을 카카오 톡캘린더에 등록",
        description = """
파티 시작 전까지만 등록할 수 있고, 파티의 호스트 또는 현재 참여자만 호출할 수 있다.
이미 등록한 파티를 다시 호출하면 기존 일정을 갱신한다.

**카카오 액세스 토큰**
클라이언트가 카카오 SDK 로 톡캘린더 동의를 받은 뒤 얻은 액세스 토큰을 `X-Kakao-Access-Token` 헤더로 전달한다.
서버는 이 토큰을 저장하지 않는다.
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "등록 또는 갱신 성공")
    @SwaggerApiResponse(
        responseCode = "400",
        description = "카카오 액세스 토큰 헤더 누락",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "KAKAO_ACCESS_TOKEN_REQUIRED",
                                "message": "카카오 액세스 토큰이 필요합니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "인증 필요",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_UNAUTHORIZED",
                                "message": "인증이 필요합니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "만료된 토큰",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_EXPIRED_TOKEN",
                                "message": "만료된 토큰입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "유효하지 않은 토큰",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_INVALID_TOKEN",
                                "message": "유효하지 않은 토큰입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "카카오 재로그인 필요",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "KAKAO_TOKEN_INVALID",
                                "message": "카카오 재로그인이 필요합니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "403",
        description = "권한 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 권한 없음",
                        value = """
                            {
                              "status": 403,
                              "error": {
                                "code": "PARTY_FORBIDDEN",
                                "message": "파티에 대한 권한이 없습니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "톡캘린더 동의 필요",
                        value = """
                            {
                              "status": 403,
                              "error": {
                                "code": "KAKAO_CALENDAR_CONSENT_REQUIRED",
                                "message": "톡캘린더 사용 동의가 필요합니다"
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
        description = "존재하지 않는 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
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
    @SwaggerApiResponse(
        responseCode = "409",
        description = "등록 불가",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "이미 시작된 파티",
                        value = """
                            {
                              "status": 409,
                              "error": {
                                "code": "TALK_CALENDAR_PARTY_ALREADY_STARTED",
                                "message": "이미 시작된 파티는 캘린더에 등록할 수 없습니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "동시 요청 충돌",
                        value = """
                            {
                              "status": 409,
                              "error": {
                                "code": "CALENDAR_REGISTRATION_IN_PROGRESS",
                                "message": "캘린더 등록이 이미 진행 중입니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "502",
        description = "카카오 톡캘린더 연동 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 502,
                              "error": {
                                "code": "KAKAO_CALENDAR_UNAVAILABLE",
                                "message": "카카오 톡캘린더 연동에 실패했습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun registerPartyEvent(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
        @Parameter(
            description = "카카오 액세스 토큰",
            `in` = ParameterIn.HEADER,
            name = "X-Kakao-Access-Token",
            required = true,
        )
        kakaoAccessToken: String?,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult>
}
