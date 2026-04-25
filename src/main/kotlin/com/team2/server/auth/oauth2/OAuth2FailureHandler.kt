package com.team2.server.auth.oauth2

import com.team2.server.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2FailureHandler(
    @Value("\${app.oauth2.authorized-redirect-uris}")
    private val allowedRedirectUris: List<String>,
) : SimpleUrlAuthenticationFailureHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        log.warn("OAuth2 authentication failed", exception)

        val target = allowedRedirectUris.first()
        val redirectUrl =
            UriComponentsBuilder
                .fromUriString(target)
                .queryParam("error", ErrorCode.AUTH_OAUTH_FAILURE.name)
                .build()
                .toUriString()

        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }
}
