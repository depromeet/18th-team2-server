package com.team2.server.auth.oauth2

import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2SuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    private val oAuth2Properties: OAuth2Properties,
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

        val target = resolveRedirectUri(request) ?: oAuth2Properties.authorizedRedirectUris.first()
        val redirectUrl =
            UriComponentsBuilder
                .fromUriString(target)
                .queryParam("token", token)
                .build()
                .toUriString()

        OAuth2RedirectUriCookies.clear(response, oAuth2Properties.cookieSecure)
        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun resolveRedirectUri(request: HttpServletRequest): String? {
        val candidate =
            OAuth2RedirectUriCookies.read(request)
                ?: request.getParameter("redirect_uri")
                ?: return null
        return if (oAuth2Properties.authorizedRedirectUris.contains(candidate)) candidate else null
    }
}
