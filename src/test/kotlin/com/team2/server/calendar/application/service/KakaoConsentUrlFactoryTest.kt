package com.team2.server.calendar.application.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KakaoConsentUrlFactoryTest {
    private val factory =
        KakaoConsentUrlFactory(
            apiBaseUrl = "https://api.example.com",
            authBaseUrl = "https://kauth.kakao.com",
            clientId = "test-client-id",
            webBaseUrl = "https://web.example.com",
        )

    @Test
    fun `동의 진입 URL 에 티켓과 복귀 경로가 인코딩되어 담긴다`() {
        val url = factory.consentEntryUrl("ticket-1", "/party/1?tab=info")

        assertTrue(url.startsWith("https://api.example.com/api/v1/kakao-calendar/consent?"), url)
        assertTrue(url.contains("ticket=ticket-1"), url)
        assertTrue(url.contains("return_path=%2Fparty%2F1%3Ftab%3Dinfo"), url)
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

    @Test
    fun `복귀 주소는 설정된 프론트 origin 에 경로를 붙인다`() {
        assertEquals("https://web.example.com/party/366", factory.returnUrl("/party/366"))
        assertEquals("https://web.example.com/", factory.returnUrl("/"))
    }

    @Test
    fun `프론트 origin 의 끝 슬래시는 정규화한다`() {
        val trailing =
            KakaoConsentUrlFactory(
                apiBaseUrl = "https://api.example.com",
                authBaseUrl = "https://kauth.kakao.com",
                clientId = "test-client-id",
                webBaseUrl = "https://web.example.com/",
            )

        assertEquals("https://web.example.com/party/366", trailing.returnUrl("/party/366"))
    }

    @Test
    fun `프론트 origin 설정이 잘못되면 생성 단계에서 거부한다`() {
        val wrong =
            listOf(
                "hapalin.com",
                "//hapalin.com",
                "ftp://hapalin.com",
                "https://user:pw@hapalin.com",
                "https://hapalin.com?a=1",
                "https://hapalin.com#x",
            )

        for (value in wrong) {
            assertFailsWith<IllegalStateException>(message = value) {
                KakaoConsentUrlFactory(
                    apiBaseUrl = "https://api.example.com",
                    authBaseUrl = "https://kauth.kakao.com",
                    clientId = "test-client-id",
                    webBaseUrl = value,
                )
            }
        }
    }

    @Test
    fun `경로가 아닌 복귀 값은 거부한다`() {
        val rejected =
            listOf(
                "https://evil.example.com",
                "//evil.example.com",
                "/\\evil.example.com",
                "party/366",
                "",
                "/party/366#top",
                "/party/366\n",
                // 쿠키 값에 들어가면 톰캣이 Set-Cookie 생성에서 예외를 던진다
                "/party/1?tab=a,b",
                "/party/1?a=b;c",
                // URL 조립 단계에서 예외가 나는 값들
                "/search?q=hello world",
                "/party/1?q=%zz",
                "/party/1?q=100%",
            )

        for (value in rejected) {
            assertFalse(factory.isValidReturnPath(value), value)
        }
    }

    @Test
    fun `정상 경로는 통과한다`() {
        for (value in listOf("/", "/party/366", "/party/366?tab=info", "/my-page/calendar", "/q?s=%20x")) {
            assertTrue(factory.isValidReturnPath(value), value)
        }
    }
}
