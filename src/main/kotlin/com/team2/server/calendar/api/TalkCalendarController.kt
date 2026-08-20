package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCase
import com.team2.server.calendar.application.usecase.ResolveKakaoCalendarAccessTokenUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class TalkCalendarController(
    private val resolveKakaoCalendarAccessTokenUseCase: ResolveKakaoCalendarAccessTokenUseCase,
    private val registerPartyTalkCalendarEventUseCase: RegisterPartyTalkCalendarEventUseCase,
) : TalkCalendarApi {
    /**
     * 토큰 확보와 일정 등록은 서로 다른 트랜잭션이다.
     * 여기서 순서대로 부르는 이유는 한 UseCase 안에서 나누면 같은 빈 자기호출이 되어
     * Spring 프록시를 타지 않고, 그러면 트랜잭션이 아예 걸리지 않기 때문이다.
     */
    @PostMapping("/{partyId}/talk-calendar")
    override fun registerPartyEvent(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult> {
        val accessToken =
            resolveKakaoCalendarAccessTokenUseCase(principal.userId)
                ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
        return ApiResponse.success(
            HttpStatus.OK,
            registerPartyTalkCalendarEventUseCase(
                RegisterPartyTalkCalendarEventCommand(
                    partyId = partyId,
                    userId = principal.userId,
                    kakaoAccessToken = accessToken,
                ),
            ),
        )
    }
}
