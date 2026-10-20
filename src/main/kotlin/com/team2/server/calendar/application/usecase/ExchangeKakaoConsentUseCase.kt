package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import org.springframework.stereotype.Service

/**
 * 인가 코드를 액세스 토큰과 카카오 회원번호로 바꾼다.
 *
 * 외부 호출만 하므로 트랜잭션을 걸지 않는다. 실패를 예외가 아니라 `null` 로 알리는 이유는
 * 호출자가 사유를 리다이렉트 파라미터로 전달해야 하고, 되감을 쓰기가 없기 때문이다.
 */
@Service
class ExchangeKakaoConsentUseCase(
    private val kakaoOAuthPort: KakaoOAuthPort,
    private val kakaoAccountPort: KakaoAccountPort,
) {
    operator fun invoke(
        code: String,
        redirectUri: String,
    ): KakaoConsentCredentials? {
        val tokens = kakaoOAuthPort.exchange(code, redirectUri) ?: return null
        val providerId = kakaoAccountPort.fetchProviderId(tokens.accessToken) ?: return null
        return KakaoConsentCredentials(accessToken = tokens.accessToken, kakaoProviderId = providerId)
    }
}
