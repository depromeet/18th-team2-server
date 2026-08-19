package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Talk Calendar", description = "카카오 톡캘린더 연동 API")
interface KakaoCalendarConnectionApi {
    @Operation(
        summary = "톡캘린더 동의 URL 발급",
        description = """
브라우저를 반환된 `consentUrl` 로 보내면 카카오 동의를 거쳐 `redirectUri` 로 돌아온다.
복귀 시 쿼리 파라미터 `calendarConsent` 에 결과가 담긴다
(`granted` / `denied` / `account_mismatch` / `expired` / `failed`).

일정 등록이 403 `KAKAO_CALENDAR_CONSENT_REQUIRED` 를 반환했을 때 이 엔드포인트를 호출한다.
마이페이지에서 미리 연동하는 흐름에도 같은 엔드포인트를 쓴다.
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "발급 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun issueConsentUrl(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "동의 후 돌아올 프론트 주소", example = "https://hapalin.com/mypage")
        redirectUri: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult>

    @Operation(
        summary = "톡캘린더 연동 해제",
        description = "서버에 저장된 카카오 토큰을 지운다. 카카오 계정 전체 연결은 끊지 않는다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "204", description = "해제 완료. 연동이 없어도 204")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun disconnect(
        @Parameter(hidden = true) principal: UserPrincipal,
    )
}
