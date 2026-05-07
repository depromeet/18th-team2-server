package com.team2.server.auth.oauth2

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

internal object OAuth2RedirectUriCookies {
    const val NAME = "OAUTH2_REDIRECT_URI"
    private const val PATH = "/"
    private const val MAX_AGE_SECONDS = 180

    fun read(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == NAME }?.value

    fun write(
        response: HttpServletResponse,
        value: String,
        secure: Boolean,
    ) {
        val cookie =
            Cookie(NAME, value).apply {
                path = PATH
                isHttpOnly = true
                maxAge = MAX_AGE_SECONDS
                this.secure = secure
                setAttribute("SameSite", "Lax")
            }
        response.addCookie(cookie)
    }

    fun clear(
        response: HttpServletResponse,
        secure: Boolean,
    ) {
        val cookie =
            Cookie(NAME, "").apply {
                path = PATH
                isHttpOnly = true
                maxAge = 0
                this.secure = secure
                setAttribute("SameSite", "Lax")
            }
        response.addCookie(cookie)
    }
}
