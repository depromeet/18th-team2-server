package com.team2.server.auth.oauth2

import com.team2.server.auth.FakeUserRepository
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import java.lang.reflect.Field
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2SuccessHandlerTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val tokenProvider = JwtTokenProvider(JwtProperties(secret = secret))

    private fun seedUser(
        repo: FakeUserRepository,
        id: Long,
    ): User {
        val u =
            User(
                name = "n",
                birthDay = "01-01",
                provider = AuthProvider.KAKAO,
                providerId = "p$id",
                email = "$id@kakao.local",
            )
        val idField: Field = u.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(u, id)
        repo.save(u)
        return u
    }

    private fun handler(
        repo: FakeUserRepository,
        allowed: List<String> = listOf("http://localhost:3000/oauth/redirect"),
    ): OAuth2SuccessHandler = OAuth2SuccessHandler(tokenProvider, repo, OAuth2Properties(allowed))

    private fun authWith(principal: UserPrincipal): Authentication {
        val auth = mock<Authentication>()
        whenever(auth.principal).thenReturn(principal)
        return auth
    }

    private fun redirectUriCookie(value: String): Cookie = Cookie(OAuth2RedirectUriCookies.NAME, value)

    @Test
    fun `허용 redirect_uri 쿼리에 토큰 포함하여 302`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 10L)
        val req =
            MockHttpServletRequest().apply {
                setParameter(
                    "redirect_uri",
                    "http://localhost:3000/oauth/redirect",
                )
            }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo).onAuthenticationSuccess(req, res, auth)

        assertEquals(302, res.status)
        val location = res.getHeader("Location")
        assertNotNull(location)
        assertTrue(location.startsWith("http://localhost:3000/oauth/redirect?token="))
    }

    @Test
    fun `redirect_uri 미지정 시 디폴트(첫번째 허용목록) 사용`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 11L)
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("https://app.example.com/cb", "https://other.example.com/cb"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?token="))
    }

    @Test
    fun `허용 목록에 없는 redirect_uri는 디폴트로 폴백`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 12L)
        val req = MockHttpServletRequest().apply { setParameter("redirect_uri", "https://evil.example.com/steal") }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("https://app.example.com/cb"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?token="))
    }

    @Test
    fun `쿠키에 redirect_uri가 있으면 해당 값으로 302 후 쿠키 삭제`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 13L)
        val req =
            MockHttpServletRequest().apply {
                setCookies(redirectUriCookie("http://localhost:5173/oauth/callback"))
            }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("http://localhost:5173/oauth/callback", "https://hapalin.com/oauth/redirect"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("http://localhost:5173/oauth/callback?token="))
        val cleared = res.getCookie(OAuth2RedirectUriCookies.NAME) ?: error("expected clear cookie")
        assertEquals(0, cleared.maxAge)
    }

    @Test
    fun `쿠키와 쿼리가 모두 있을 때 쿠키 값을 우선 사용`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 14L)
        val req =
            MockHttpServletRequest().apply {
                setCookies(redirectUriCookie("http://localhost:5173/oauth/callback"))
                setParameter("redirect_uri", "https://hapalin.com/oauth/redirect")
            }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("http://localhost:5173/oauth/callback", "https://hapalin.com/oauth/redirect"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("http://localhost:5173/oauth/callback?token="))
    }

    @Test
    fun `쿠키 값이 화이트리스트에 없으면 폴백`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 15L)
        val req =
            MockHttpServletRequest().apply {
                setCookies(redirectUriCookie("https://evil.example.com/steal"))
            }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("https://app.example.com/cb"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?token="))
    }
}
