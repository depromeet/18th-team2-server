package com.team2.server.auth.oauth2

import com.team2.server.auth.config.OAuth2Properties
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.AuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuth2FailureHandlerTest {
    private fun handler(allowed: List<String>) = OAuth2FailureHandler(OAuth2Properties(allowed))

    private val ex = object : AuthenticationException("boom") {}

    @Test
    fun `쿠키에 redirect_uri 있으면 해당 값으로 에러 리다이렉트 + 쿠키 삭제`() {
        val req =
            MockHttpServletRequest().apply {
                setCookies(Cookie(OAuth2RedirectUriCookies.NAME, "http://localhost:5173/oauth/callback"))
            }
        val res = MockHttpServletResponse()

        handler(listOf("http://localhost:5173/oauth/callback", "https://hapalin.com/oauth/redirect"))
            .onAuthenticationFailure(req, res, ex)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("http://localhost:5173/oauth/callback?error="))
        val cleared = res.getCookie(OAuth2RedirectUriCookies.NAME) ?: error("expected clear cookie")
        assertEquals(0, cleared.maxAge)
    }

    @Test
    fun `쿠키 없으면 디폴트(첫번째 허용목록)로 에러 리다이렉트`() {
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()

        handler(listOf("https://app.example.com/cb"))
            .onAuthenticationFailure(req, res, ex)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?error="))
    }

    @Test
    fun `허용 목록에 없는 쿠키 값은 디폴트로 폴백`() {
        val req =
            MockHttpServletRequest().apply {
                setCookies(Cookie(OAuth2RedirectUriCookies.NAME, "https://evil.example.com/steal"))
            }
        val res = MockHttpServletResponse()

        handler(listOf("https://app.example.com/cb"))
            .onAuthenticationFailure(req, res, ex)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?error="))
    }
}
