package com.team2.server.auth.oauth2

import com.team2.server.auth.config.OAuth2Properties
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuth2RedirectUriCaptureFilterTest {
    private fun filter(allowed: List<String> = listOf("http://localhost:5173/oauth/callback")) =
        OAuth2RedirectUriCaptureFilter(OAuth2Properties(allowed))

    @Test
    fun `oauth2 인가 요청 경로에서 허용 redirect_uri 쿼리를 쿠키로 저장`() {
        val req =
            MockHttpServletRequest("GET", "/oauth2/authorization/kakao").apply {
                servletPath = "/oauth2/authorization/kakao"
                setParameter("redirect_uri", "http://localhost:5173/oauth/callback")
            }
        val res = MockHttpServletResponse()

        filter().doFilter(req, res, MockFilterChain())

        val cookie = res.getCookie(OAuth2RedirectUriCookies.NAME) ?: error("expected cookie")
        assertEquals("http://localhost:5173/oauth/callback", cookie.value)
        assertEquals(true, cookie.isHttpOnly)
        assertEquals("/", cookie.path)
    }

    @Test
    fun `redirect_uri 파라미터가 없으면 쿠키 미설정`() {
        val req =
            MockHttpServletRequest("GET", "/oauth2/authorization/kakao").apply {
                servletPath = "/oauth2/authorization/kakao"
            }
        val res = MockHttpServletResponse()

        filter().doFilter(req, res, MockFilterChain())

        assertNull(res.getCookie(OAuth2RedirectUriCookies.NAME))
    }

    @Test
    fun `허용 목록에 없는 redirect_uri는 쿠키 미설정 (오픈 리다이렉트 방어)`() {
        val req =
            MockHttpServletRequest("GET", "/oauth2/authorization/kakao").apply {
                servletPath = "/oauth2/authorization/kakao"
                setParameter("redirect_uri", "https://evil.example.com/steal")
            }
        val res = MockHttpServletResponse()

        filter(allowed = listOf("http://localhost:5173/oauth/callback"))
            .doFilter(req, res, MockFilterChain())

        assertNull(res.getCookie(OAuth2RedirectUriCookies.NAME))
    }

    @Test
    fun `oauth2 인가 요청 경로가 아니면 쿼리가 있어도 쿠키 미설정`() {
        val req =
            MockHttpServletRequest("GET", "/api/v1/foo").apply {
                servletPath = "/api/v1/foo"
                setParameter("redirect_uri", "http://localhost:5173/oauth/callback")
            }
        val res = MockHttpServletResponse()

        filter().doFilter(req, res, MockFilterChain())

        assertNull(res.getCookie(OAuth2RedirectUriCookies.NAME))
    }
}
