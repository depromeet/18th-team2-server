package com.team2.server.auth.oauth2

import com.team2.server.auth.config.OAuth2Properties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class OAuth2RedirectUriCaptureFilter(
    private val oAuth2Properties: OAuth2Properties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.servletPath.startsWith(AUTHORIZATION_BASE_URI)) {
            val redirectUri = request.getParameter(REDIRECT_URI_PARAM)
            if (!redirectUri.isNullOrBlank() && oAuth2Properties.authorizedRedirectUris.contains(redirectUri)) {
                OAuth2RedirectUriCookies.write(response, redirectUri, oAuth2Properties.cookieSecure)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val AUTHORIZATION_BASE_URI = "/oauth2/authorization/"
        const val REDIRECT_URI_PARAM = "redirect_uri"
    }
}
