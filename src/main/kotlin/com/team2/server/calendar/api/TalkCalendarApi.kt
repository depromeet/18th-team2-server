package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
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

**에러 코드**
- `KAKAO_ACCESS_TOKEN_REQUIRED` (400): 헤더 누락
- `KAKAO_TOKEN_INVALID` (401): 카카오 재로그인 필요
- `KAKAO_CALENDAR_CONSENT_REQUIRED` (403): 톡캘린더 추가 동의 필요
- `TALK_CALENDAR_PARTY_ALREADY_STARTED` (409): 이미 시작된 파티
- `CALENDAR_REGISTRATION_IN_PROGRESS` (409): 동시 요청 충돌
- `KAKAO_CALENDAR_UNAVAILABLE` (502): 카카오 장애 또는 타임아웃
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "등록 또는 갱신 성공")
    @AuthErrorResponses
    @ForbiddenResponse
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
