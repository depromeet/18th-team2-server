package com.team2.server.calendar.application.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KakaoConsentUrlFactoryTest {
    private val factory =
        KakaoConsentUrlFactory(
            apiBaseUrl = "https://api.example.com",
            authBaseUrl = "https://kauth.kakao.com",
            clientId = "test-client-id",
        )

    @Test
    fun `동의 진입 URL 에 티켓과 복귀 주소가 인코딩되어 담긴다`() {
        val url = factory.consentEntryUrl("ticket-1", "https://web.example.com/party/1")

        assertTrue(url.startsWith("https://api.example.com/api/v1/kakao-calendar/consent?"), url)
        assertTrue(url.contains("ticket=ticket-1"), url)
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fweb.example.com%2Fparty%2F1"), url)
    }

    @Test
    fun `카카오 인가 URL 은 talk_calendar scope 와 티켓 state 를 쓴다`() {
        val url = factory.kakaoAuthorizeUrl("ticket-1")

        assertTrue(url.startsWith("https://kauth.kakao.com/oauth/authorize?"), url)
        assertTrue(url.contains("client_id=test-client-id"), url)
        assertTrue(url.contains("response_type=code"), url)
        assertTrue(url.contains("scope=talk_calendar"), url)
        assertTrue(url.contains("state=ticket-1"), url)
        assertTrue(
            url.contains("redirect_uri=https%3A%2F%2Fapi.example.com%2Fapi%2Fv1%2Fkakao-calendar%2Fconsent%2Fcallback"),
            url,
        )
    }

    @Test
    fun `scope 는 할 일 권한이 아니라 일정 권한이다`() {
        assertTrue(factory.kakaoAuthorizeUrl("t").contains("scope=talk_calendar&"))
        assertTrue(!factory.kakaoAuthorizeUrl("t").contains("talk_calendar_task"))
    }

    @Test
    fun `콜백 주소는 토큰 교환 때와 같은 값을 쓴다`() {
        assertEquals("https://api.example.com/api/v1/kakao-calendar/consent/callback", factory.callbackUri())
    }
}
