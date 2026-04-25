package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KakaoAttributesTest {
    @Test
    fun `정상 응답 파싱`() {
        val raw = mapOf(
            "id" to 123456789L,
            "kakao_account" to mapOf(
                "email" to "user@kakao.com",
                "profile" to mapOf("nickname" to "홍길동"),
            ),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals(AuthProvider.KAKAO, attrs.provider)
        assertEquals("123456789", attrs.providerId)
        assertEquals("user@kakao.com", attrs.email)
        assertEquals("홍길동", attrs.nickname)
    }

    @Test
    fun `email 미동의 시 fallback`() {
        val raw = mapOf(
            "id" to 999L,
            "kakao_account" to mapOf(
                "profile" to mapOf("nickname" to "닉"),
            ),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals("999@kakao.local", attrs.email)
        assertEquals("닉", attrs.nickname)
    }

    @Test
    fun `profile 누락 시 nickname fallback`() {
        val raw = mapOf(
            "id" to 555L,
            "kakao_account" to mapOf("email" to "x@kakao.com"),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals("사용자555", attrs.nickname)
        assertEquals("x@kakao.com", attrs.email)
    }

    @Test
    fun `kakao_account 누락 시 모두 fallback`() {
        val raw = mapOf<String, Any>("id" to 7L)

        val attrs = KakaoAttributes(raw)

        assertEquals("7", attrs.providerId)
        assertEquals("7@kakao.local", attrs.email)
        assertEquals("사용자7", attrs.nickname)
    }
}
