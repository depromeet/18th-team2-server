package com.team2.server.calendar.api

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.usecase.IssueGuestKakaoCalendarConsentUrlUseCase
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 비회원이 톡캘린더 동의를 시작하는 경로. 인증을 요구하지 않는다. */
@RestController
@RequestMapping("/api/v1/party-invites")
class GuestTalkCalendarConsentController(
    private val issueGuestKakaoCalendarConsentUrlUseCase: IssueGuestKakaoCalendarConsentUrlUseCase,
) : GuestTalkCalendarConsentApi {
    @GetMapping("/{inviteToken}/talk-calendar/consent-url")
    override fun issueConsentUrl(
        @PathVariable inviteToken: String,
        @RequestParam returnPath: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult> =
        ApiResponse.success(
            HttpStatus.OK,
            issueGuestKakaoCalendarConsentUrlUseCase(inviteToken, returnPath),
        )
}
