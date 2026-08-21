package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 저장된 토큰을 지운다.
 *
 * 카카오 쪽 연결 해제(`unlink`)는 하지 않는다. 앱 전체 연결이 끊겨 사용자가 로그인조차 할 수 없게 되므로
 * 캘린더 연동만 끊는다는 의도와 맞지 않는다.
 */
@Service
class DisconnectKakaoCalendarUseCase(
    private val kakaoCalendarConnectionService: KakaoCalendarConnectionService,
) {
    @Transactional
    operator fun invoke(userId: Long) {
        kakaoCalendarConnectionService.find(userId)?.let { kakaoCalendarConnectionService.delete(it) }
    }
}
