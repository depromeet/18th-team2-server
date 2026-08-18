package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class TalkCalendarController(
    private val registerPartyTalkCalendarEventUseCase: RegisterPartyTalkCalendarEventUseCase,
) : TalkCalendarApi {
    @PostMapping("/{partyId}/talk-calendar")
    override fun registerPartyEvent(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
        @RequestHeader(value = "X-Kakao-Access-Token", required = false) kakaoAccessToken: String?,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult> {
        if (kakaoAccessToken.isNullOrBlank()) {
            throw BusinessException(ErrorCode.KAKAO_ACCESS_TOKEN_REQUIRED)
        }
        return ApiResponse.success(
            HttpStatus.OK,
            registerPartyTalkCalendarEventUseCase(
                RegisterPartyTalkCalendarEventCommand(
                    partyId = partyId,
                    userId = principal.userId,
                    kakaoAccessToken = kakaoAccessToken,
                ),
            ),
        )
    }
}
