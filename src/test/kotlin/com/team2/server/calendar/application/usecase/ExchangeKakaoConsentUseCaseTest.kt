package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeKakaoConsentUseCaseTest {
    private val tokens =
        KakaoOAuthTokens(
            accessToken = "guest-access",
            accessTokenExpiresInSeconds = 21599,
            refreshToken = "guest-refresh",
            refreshTokenExpiresInSeconds = 5183999,
        )

    private fun useCase(
        exchangeResult: KakaoOAuthTokens?,
        providerId: String?,
    ): ExchangeKakaoConsentUseCase =
        ExchangeKakaoConsentUseCase(
            kakaoOAuthPort =
                object : KakaoOAuthPort {
                    override fun exchange(
                        code: String,
                        redirectUri: String,
                    ): KakaoOAuthTokens? = exchangeResult

                    override fun refresh(refreshToken: String): KakaoOAuthTokens? =
                        throw UnsupportedOperationException("이 테스트는 refresh 를 쓰지 않는다")
                },
            kakaoAccountPort =
                object : KakaoAccountPort {
                    override fun fetchProviderId(accessToken: String): String? = providerId
                },
        )

    @Test
    fun `토큰 교환과 회원번호 확인이 성공하면 자격증명을 돌려준다`() {
        val result = useCase(tokens, "4321")("code", "https://api.example.com/callback")

        assertEquals("guest-access", result?.accessToken)
        assertEquals("4321", result?.kakaoProviderId)
    }

    @Test
    fun `카카오가 토큰 교환을 거부하면 null 이다`() {
        assertNull(useCase(null, "4321")("code", "https://api.example.com/callback"))
    }

    @Test
    fun `회원번호를 확인할 수 없으면 null 이다`() {
        assertNull(useCase(tokens, null)("code", "https://api.example.com/callback"))
    }
}
