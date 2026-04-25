package com.team2.server.auth.oauth2

import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2SuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    @Value("\${app.oauth2.authorized-redirect-uris}")
    private val allowedRedirectUris: List<String>,
) : SimpleUrlAuthenticationSuccessHandler() {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as UserPrincipal
        val user =
            userRepository
                .findById(principal.userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        val token = jwtTokenProvider.issue(user)

        val target = resolveRedirectUri(request) ?: allowedRedirectUris.first()
        val redirectUrl =
            UriComponentsBuilder
                .fromUriString(target)
                .queryParam("token", token)
                .build()
                .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun resolveRedirectUri(request: HttpServletRequest): String? {
        val candidate = request.getParameter("redirect_uri") ?: return null
        return if (allowedRedirectUris.contains(candidate)) candidate else null
    }
}
