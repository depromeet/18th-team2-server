package com.team2.server.auth.jwt

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.lang.reflect.Field
import java.util.Base64
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val tokenProvider = JwtTokenProvider(JwtProperties(secret = secret, expirationHours = 24))

    private fun userWithId(id: Long): User {
        val user =
            User(
                name = "n",
                birthDay = "01-01",
                provider = AuthProvider.KAKAO,
                providerId = "p$id",
                email = "$id@kakao.local",
            )
        val idField: Field = user.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
        return user
    }

    private fun newFilter(repo: UserRepository): JwtAuthenticationFilter = JwtAuthenticationFilter(tokenProvider, repo)

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `유효한 Bearer 토큰은 SecurityContext에 인증 세팅`() {
        val user = userWithId(7L)
        val repo = mock<UserRepository>()
        whenever(repo.findById(7L)).thenReturn(Optional.of(user))
        val token = tokenProvider.issue(user)

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals(7L, (auth.principal as UserPrincipal).userId)
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }

    @Test
    fun `만료 토큰은 컨텍스트 미설정 + 요청 속성에 EXPIRED 코드`() {
        val user = userWithId(1L)
        val expiredProvider = JwtTokenProvider(JwtProperties(secret = secret, expirationHours = 0))
        val repo = mock<UserRepository>()
        val expired = expiredProvider.issue(user)
        Thread.sleep(50)

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $expired") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_EXPIRED_TOKEN, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }

    @Test
    fun `잘못된 시그니처는 INVALID_TOKEN 속성`() {
        val other =
            JwtTokenProvider(
                JwtProperties(
                    secret = Base64.getEncoder().encodeToString(ByteArray(64) { (it + 1).toByte() }),
                    expirationHours = 24,
                ),
            )
        val token = other.issue(userWithId(2L))
        val repo = mock<UserRepository>()

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_INVALID_TOKEN, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
    }

    @Test
    fun `userId DB 미존재면 USER_NOT_FOUND 속성`() {
        val token = tokenProvider.issue(userWithId(99L))
        val repo = mock<UserRepository>()
        whenever(repo.findById(99L)).thenReturn(Optional.empty())

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
    }

    @Test
    fun `Authorization 헤더 없으면 통과 + 컨텍스트 미설정`() {
        val repo = mock<UserRepository>()
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNull(req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }
}
