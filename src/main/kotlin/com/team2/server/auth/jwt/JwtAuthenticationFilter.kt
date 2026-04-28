package com.team2.server.auth.jwt

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.repository.UserRepository
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val BEARER_PREFIX = "Bearer "

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token == null || SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        val authenticated = authenticate(token, request)
        if (!authenticated) {
            jwtAuthenticationEntryPoint.commence(
                request,
                response,
                InsufficientAuthenticationException("Invalid authentication token"),
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX)) header.removePrefix(BEARER_PREFIX).trim() else null
    }

    private fun authenticate(
        token: String,
        request: HttpServletRequest,
    ): Boolean {
        try {
            val claims = jwtTokenProvider.parse(token)
            val userId = claims.subject.toLong()
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_USER_NOT_FOUND)
                return false
            }
            val principal = UserPrincipal.from(user)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
            return true
        } catch (e: ExpiredJwtException) {
            log.debug("Expired JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_EXPIRED_TOKEN)
            return false
        } catch (e: JwtException) {
            log.debug("Invalid JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN)
            return false
        } catch (e: IllegalArgumentException) {
            log.debug("Malformed JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN)
            return false
        }
    }
}
