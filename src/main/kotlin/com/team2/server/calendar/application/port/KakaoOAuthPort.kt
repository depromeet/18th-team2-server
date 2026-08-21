package com.team2.server.calendar.application.port

/**
 * 카카오 인증 서버(`kauth.kakao.com`) 와의 토큰 교환.
 *
 * 반환값이 `null` 이면 카카오가 요청을 거부한 것이다(만료된 리프레시 토큰, 철회된 동의 등).
 * 이 경우 재시도해도 소용없으므로 호출자는 연동을 정리하고 다시 동의를 받아야 한다.
 * 카카오에 닿지 못했거나 장애인 경우에는 예외를 던진다 — 그건 나중에 다시 시도할 수 있는 상황이다.
 */
interface KakaoOAuthPort {
    fun exchange(
        code: String,
        redirectUri: String,
    ): KakaoOAuthTokens?

    fun refresh(refreshToken: String): KakaoOAuthTokens?
}

data class KakaoOAuthTokens(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String?,
    val refreshTokenExpiresInSeconds: Long?,
)
