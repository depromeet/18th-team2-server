package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

enum class ConsentOutcome {
    GRANTED,
    DENIED,
    ACCOUNT_MISMATCH,
    EXPIRED,
    FAILED,
}

/**
 * 동의 콜백에서 받은 인가 코드를 토큰으로 바꿔 연동을 저장한다.
 *
 * 티켓이 지정한 사용자와 카카오가 확인해 준 계정이 일치할 때만 저장한다. 대조하지 않으면 브라우저의
 * 카카오 로그인 계정이 서비스 로그인 계정과 다를 때(공용 PC, 계정 여러 개) 토큰이 엉뚱한 사용자 행에
 * 저장되어, 한쪽은 연동했는데도 계속 동의를 요구받고 다른 쪽 캘린더에는 모르는 일정이 등록된다.
 *
 * 카카오 계정에 대응하는 서비스 사용자가 없는 경우도 같은 실패로 다룬다. 추가 동의는 기존 사용자의
 * 권한을 확장하는 경로이지 가입 경로가 아니다.
 *
 * 리프레시 토큰 만료가 응답에 없으면 만료 시각을 모른다는 뜻이다. `now` 로 대신 채우면 저장 직후
 * 만료로 판정돼 다음 요청에서 연동이 지워지므로, 기존 연동은 갖고 있던 만료를 지키고 신규 연동은
 * 실패로 돌린다.
 */
@Service
class SaveKakaoCalendarConsentUseCase(
    private val kakaoOAuthPort: KakaoOAuthPort,
    private val kakaoAccountPort: KakaoAccountPort,
    private val calendarUserPort: CalendarUserPort,
    private val kakaoCalendarConnectionService: KakaoCalendarConnectionService,
    private val clock: Clock,
) {
    @Suppress("ReturnCount")
    @Transactional
    operator fun invoke(
        code: String,
        ticketUserId: Long,
        redirectUri: String,
    ): ConsentOutcome {
        val tokens = kakaoOAuthPort.exchange(code, redirectUri) ?: return ConsentOutcome.FAILED
        val providerId = kakaoAccountPort.fetchProviderId(tokens.accessToken) ?: return ConsentOutcome.FAILED
        if (calendarUserPort.findUserIdByKakaoProviderId(providerId) != ticketUserId) {
            return ConsentOutcome.ACCOUNT_MISMATCH
        }

        val now = LocalDateTime.now(clock)
        val accessTokenExpiresAt = now.plusSeconds(tokens.accessTokenExpiresInSeconds)
        val refreshTokenExpiresAt = tokens.refreshTokenExpiresInSeconds?.let { now.plusSeconds(it) }
        val existing = kakaoCalendarConnectionService.find(ticketUserId)
        if (existing != null) {
            existing.applyRefreshed(
                accessToken = tokens.accessToken,
                accessTokenExpiresAt = accessTokenExpiresAt,
                refreshToken = tokens.refreshToken,
                refreshTokenExpiresAt = refreshTokenExpiresAt,
            )
            return ConsentOutcome.GRANTED
        }
        kakaoCalendarConnectionService.save(
            KakaoCalendarConnection(
                userId = ticketUserId,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: return ConsentOutcome.FAILED,
                accessTokenExpiresAt = accessTokenExpiresAt,
                refreshTokenExpiresAt = refreshTokenExpiresAt ?: return ConsentOutcome.FAILED,
            ),
        )
        return ConsentOutcome.GRANTED
    }
}
