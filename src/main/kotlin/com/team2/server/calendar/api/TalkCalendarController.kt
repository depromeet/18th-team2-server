package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.usecase.DisconnectKakaoCalendarUseCase
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
    private val disconnectKakaoCalendarUseCase: DisconnectKakaoCalendarUseCase,
) : TalkCalendarApi {
    /**
     * 토큰 확보와 일정 등록은 서로 다른 트랜잭션이다.
     * 여기서 순서대로 부르는 이유는 한 UseCase 안에서 나누면 같은 빈 자기호출이 되어
     * Spring 프록시를 타지 않고, 그러면 트랜잭션이 아예 걸리지 않기 때문이다.
     *
     * 동의 없음(연동 자체가 없을 때), 카카오의 토큰 거부를 되던지는 경우, 그 catch 안에서 연동을
     * 지우고 다시 던지는 경우까지 사유별로 구분되는 throw 가 3개다. 하나로 합치면 클라이언트가
     * 왜 403 을 받았는지 구분할 수 없게 되므로 로직은 그대로 두고 [Suppress] 로 억제한다.
     */
    @Suppress("ThrowsCount")
    @PostMapping("/{partyId}/talk-calendar")
    override fun registerPartyEvent(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult> {
        val accessToken =
            resolveKakaoCalendarAccessTokenUseCase(principal.userId)
                ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
        return try {
            ApiResponse.success(
                HttpStatus.OK,
                registerPartyTalkCalendarEventUseCase(
                    RegisterPartyTalkCalendarEventCommand(
                        partyId = partyId,
                        userId = principal.userId,
                        kakaoAccessToken = accessToken,
                    ),
                ),
            )
        } catch (e: BusinessException) {
            if (e.errorCode != ErrorCode.KAKAO_TOKEN_INVALID) throw e
            // 카카오가 저장된 토큰을 거부했다. 우리 시계로는 만료 전이지만 사용자가 카카오에서
            // 연결을 끊은 경우다. 죽은 연동을 지우고 동의를 다시 받게 한다. 401 을 그대로 내보내면
            // 프론트 공통 인터셉터가 세션 만료로 읽어 사용자를 로그아웃시킨다.
            disconnectKakaoCalendarUseCase(principal.userId)
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
        }
    }
}
