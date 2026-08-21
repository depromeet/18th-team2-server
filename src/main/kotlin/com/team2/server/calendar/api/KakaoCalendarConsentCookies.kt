package com.team2.server.calendar.api

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val TICKET_COOKIE = "kakao_calendar_consent_ticket"
private const val REDIRECT_URI_COOKIE = "kakao_calendar_consent_redirect_uri"
private const val MAX_AGE_SECONDS = 300

/**
 * 동의 진입에서 콜백까지 티켓과 복귀 주소를 나른다.
 *
 * 서버 메모리가 아니라 쿠키에 두는 이유는 blue/green 배포에서 콜백이 다른 인스턴스로 도착해도
 * 검증이 성립해야 하기 때문이다. 수명은 티켓과 같은 5분이다.
 */
object KakaoCalendarConsentCookies {
    fun write(
        response: HttpServletResponse,
        ticket: String,
        redirectUri: String,
        secure: Boolean,
    ) {
        response.addCookie(cookie(TICKET_COOKIE, ticket, MAX_AGE_SECONDS, secure))
        response.addCookie(cookie(REDIRECT_URI_COOKIE, redirectUri, MAX_AGE_SECONDS, secure))
    }

    fun readTicket(request: HttpServletRequest): String? = read(request, TICKET_COOKIE)

    fun readRedirectUri(request: HttpServletRequest): String? = read(request, REDIRECT_URI_COOKIE)

    fun clear(
        response: HttpServletResponse,
        secure: Boolean,
    ) {
        response.addCookie(cookie(TICKET_COOKIE, "", 0, secure))
        response.addCookie(cookie(REDIRECT_URI_COOKIE, "", 0, secure))
    }

    private fun read(
        request: HttpServletRequest,
        name: String,
    ): String? =
        request.cookies
            ?.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun cookie(
        name: String,
        value: String,
        maxAge: Int,
        secure: Boolean,
    ): Cookie =
        Cookie(name, value).apply {
            path = "/"
            isHttpOnly = true
            this.secure = secure
            this.maxAge = maxAge
            // 콜백은 카카오에서 오는 교차 사이트 최상위 이동이다. Strict 면 쿠키가 실리지 않아 플로우가 깨진다.
            setAttribute("SameSite", "Lax")
        }
}
