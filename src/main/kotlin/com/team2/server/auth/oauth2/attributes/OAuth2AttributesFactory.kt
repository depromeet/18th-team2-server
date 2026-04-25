package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

object OAuth2AttributesFactory {
    fun of(
        registrationId: String,
        raw: Map<String, Any>,
    ): OAuth2Attributes {
        val provider = AuthProvider.valueOf(registrationId.uppercase())
        return when (provider) {
            AuthProvider.KAKAO -> KakaoAttributes(raw)
            AuthProvider.GOOGLE,
            AuthProvider.APPLE,
            AuthProvider.NAVER,
            ->
                throw OAuth2AuthenticationException(
                    OAuth2Error("unsupported_provider", "지원하지 않는 provider: $provider", null),
                )
        }
    }
}
