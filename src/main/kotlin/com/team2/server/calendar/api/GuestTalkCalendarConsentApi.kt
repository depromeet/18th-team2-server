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
복귀 시 쿼리 파라미터 `calendarConsent` 에 결과가 담긴다. 비회원 경로가 돌려보내는 값은 다음 7개다.

- `registered` — 일정을 만들었다
- `already_registered` — 같은 카카오 계정이 이 파티를 이미 등록했다 (일정을 새로 만들지 않았다)
- `party_already_started` — 파티가 이미 시작돼 등록하지 않았다
- `invite_invalid` — 초대 토큰이 없거나 만료됐다
- `denied` — 사용자가 카카오 동의를 거부했다
- `expired` — 티켓이 만료됐거나 콜백의 `state` 가 맞지 않다 (동의를 다시 시작해야 한다)
- `failed` — 그 밖의 실패 (토큰 교환 실패, 카카오 오류 등)

회원 경로의 `granted` / `account_mismatch` 는 이 경로에서 나오지 않는다. 비회원은 대조할 서비스 계정이
없어 계정 대조를 하지 않고, 동의 결과만 알리는 대신 등록 결과를 그대로 돌려보낸다.

초대 토큰의 유효성은 이 엔드포인트에서 검사하지 않는다. 토큰이 잘못됐으면 콜백에서 등록을
시도할 때 `invite_invalid` 로 드러난다.

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
