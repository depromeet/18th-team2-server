package com.team2.server.calendar.api

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Talk Calendar", description = "카카오 톡캘린더 연동 API")
interface GuestTalkCalendarConsentApi {
    @Operation(
        summary = "비회원 톡캘린더 동의 URL 발급",
        description = """
이 경로는 비회원용이며, 동의를 마치면 콜백에서 등록까지 끝난다. 클라이언트는 복귀 후 등록 API 를 재호출하지 않는다.

브라우저를 반환된 `consentUrl` 로 보내면 카카오 동의를 거쳐 `returnPath` 로 돌아온다.
복귀 시 쿼리 파라미터 `calendarConsent` 에 결과가 담긴다
(`granted` / `denied` / `account_mismatch` / `expired` / `failed`).

초대 토큰의 유효성은 이 엔드포인트에서 검사하지 않는다. 토큰이 잘못됐으면 콜백에서 등록을
시도할 때 `INVITE_INVALID` 로 드러난다.

`returnPath` 는 프론트 **경로**다. 도메인은 서버 설정(`app.web-base-url`)에서 붙이므로 넘기지 않는다.
사용자가 보고 있던 화면을 그대로 넘기면 동의 후 그 화면으로 돌아온다.
""",
    )
    @SwaggerApiResponse(responseCode = "200", description = "발급 성공")
    @InternalServerErrorResponse
    fun issueConsentUrl(
        @Parameter(description = "파티 초대 토큰", example = "a796ba5f")
        inviteToken: String,
        @Parameter(description = "동의 후 돌아올 프론트 경로. `/` 로 시작해야 한다", example = "/invite/a796ba5f")
        returnPath: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult>
}
