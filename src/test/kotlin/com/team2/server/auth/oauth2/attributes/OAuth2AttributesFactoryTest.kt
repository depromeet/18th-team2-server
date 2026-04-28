package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuth2AttributesFactoryTest {
    private val rawKakao =
        mapOf<String, Any>(
            "id" to 1L,
            "kakao_account" to mapOf("email" to "a@kakao.com", "profile" to mapOf("nickname" to "n")),
        )

    @Test
    fun `kakao registrationId는 KakaoAttributes 반환`() {
        val attrs = OAuth2AttributesFactory.of("kakao", rawKakao)

        assertTrue(attrs is KakaoAttributes)
        assertEquals(AuthProvider.KAKAO, attrs.provider)
        assertEquals("1", attrs.providerId)
    }

    @Test
    fun `대문자 KAKAO도 동작`() {
        val attrs = OAuth2AttributesFactory.of("KAKAO", rawKakao)
        assertEquals(AuthProvider.KAKAO, attrs.provider)
    }

    @Test
    fun `미지원 provider GOOGLE은 OAuth2AuthenticationException`() {
        assertThrows<OAuth2AuthenticationException> {
            OAuth2AttributesFactory.of("google", emptyMap())
        }
    }

    @Test
    fun `정의되지 않은 registrationId는 IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            OAuth2AttributesFactory.of("unknown", emptyMap())
        }
    }
}
