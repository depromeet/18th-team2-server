package com.team2.server.calendar.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "톡캘린더 동의 URL")
data class KakaoCalendarConsentUrlResult(
    @Schema(
        description = "브라우저를 이 주소로 보내면 카카오 동의를 거쳐 복귀 주소로 돌아온다",
        example = "https://api.hapalin.com/api/v1/kakao-calendar/consent?ticket=...&redirect_uri=...",
    )
    val consentUrl: String,
)
