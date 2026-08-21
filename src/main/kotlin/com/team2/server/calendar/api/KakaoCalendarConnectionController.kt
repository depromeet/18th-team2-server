package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.usecase.DisconnectKakaoCalendarUseCase
import com.team2.server.calendar.application.usecase.IssueKakaoCalendarConsentUrlUseCase
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/talk-calendar-connection")
class KakaoCalendarConnectionController(
    private val issueKakaoCalendarConsentUrlUseCase: IssueKakaoCalendarConsentUrlUseCase,
    private val disconnectKakaoCalendarUseCase: DisconnectKakaoCalendarUseCase,
) : KakaoCalendarConnectionApi {
    @GetMapping("/consent-url")
    override fun issueConsentUrl(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam redirectUri: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult> =
        ApiResponse.success(
            HttpStatus.OK,
            issueKakaoCalendarConsentUrlUseCase(principal.userId, redirectUri),
        )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    override fun disconnect(
        @AuthenticationPrincipal principal: UserPrincipal,
    ) {
        disconnectKakaoCalendarUseCase(principal.userId)
    }
}
