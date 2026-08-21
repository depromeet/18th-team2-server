package com.team2.server.calendar.application.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val CONSENT_ENTRY_PATH = "/api/v1/kakao-calendar/consent"
const val CONSENT_CALLBACK_PATH = "/api/v1/kakao-calendar/consent/callback"

/** 일정 생성·조회·편집 권한. `talk_calendar_task` 는 할 일 전용이라 쓰지 않는다. */
const val TALK_CALENDAR_SCOPE = "talk_calendar"

/**
 * 복귀 경로에 허용하는 문자.
 *
 * RFC 3986 의 pchar 에서 `,` 와 `;` 를 뺀 집합이다. 그 둘은 쿠키 값에 들어가면 톰캣이 Set-Cookie 생성
 * 단계에서 예외를 던지는데, 경로를 콜백까지 쿠키로 나르므로 여기서 미리 막는다. 필요하면 클라이언트가
 * 퍼센트 인코딩해서 넘기면 된다.
 */
private val ALLOWED_RETURN_PATH_CHARS =
    ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" + "-._~!$&'()*+=:@/?%").toSet()

private const val PERCENT_ESCAPE_LENGTH = 3

@Service
class KakaoConsentUrlFactory(
    private val apiBaseUrl: String,
    private val authBaseUrl: String,
    private val clientId: String,
    webBaseUrl: String,
) {
    @Autowired
    constructor(
        @Value("\${app.api-base-url}") apiBaseUrl: String,
        @Value("\${kakao.auth.base-url:https://kauth.kakao.com}") authBaseUrl: String,
        clientRegistrationRepository: ClientRegistrationRepository,
        @Value("\${app.web-base-url}") webBaseUrl: String,
    ) : this(
        apiBaseUrl = apiBaseUrl,
        authBaseUrl = authBaseUrl,
        clientId = clientRegistrationRepository.findByRegistrationId("kakao").clientId,
        webBaseUrl = webBaseUrl,
    )

    /** 설정 오타를 기동 시점에 드러낸다. 매 요청 검사로 미루면 "복귀가 조용히 안 맞는" 형태로만 보인다. */
    private val returnOrigin: String = normalizeOrigin(webBaseUrl)

    /** 클라이언트가 브라우저를 보낼 주소. 카카오 주소가 아니라 우리 진입점이다. */
    fun consentEntryUrl(
        ticket: String,
        returnPath: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(apiBaseUrl + CONSENT_ENTRY_PATH)
            .queryParam("ticket", encode(ticket))
            .queryParam("return_path", encode(returnPath))
            .build(true)
            .toUriString()

    fun kakaoAuthorizeUrl(ticket: String): String =
        UriComponentsBuilder
            .fromUriString("$authBaseUrl/oauth/authorize")
            .queryParam("client_id", encode(clientId))
            .queryParam("redirect_uri", encode(callbackUri()))
            .queryParam("response_type", "code")
            .queryParam("scope", TALK_CALENDAR_SCOPE)
            .queryParam("state", encode(ticket))
            .build(true)
            .toUriString()

    /** 인가 요청과 토큰 교환에서 반드시 같은 값을 써야 한다. 다르면 카카오가 거부한다. */
    fun callbackUri(): String = apiBaseUrl + CONSENT_CALLBACK_PATH

    /** origin 은 서버 설정에서 오므로 클라이언트가 호스트를 지정할 방법이 없다. */
    fun returnUrl(returnPath: String): String = returnOrigin + returnPath

    /**
     * `//evil.com` 과 `/\evil.com` 은 브라우저가 프로토콜 상대 주소로 읽어 다른 호스트로 나간다.
     * fragment 는 뒤에 결과 파라미터를 붙이면 `#` 뒤로 밀려 값이 사라진다.
     *
     * 경로는 이미 인코딩된 값으로 받는다. 서버가 다시 인코딩하면 클라이언트의 `%20` 이 `%2520` 이 되므로,
     * 재인코딩하지 않는 대신 그대로 URL 에 넣어도 안전한 문자만 통과시킨다.
     */
    fun isValidReturnPath(returnPath: String): Boolean =
        returnPath.startsWith("/") &&
            !returnPath.startsWith("//") &&
            !returnPath.startsWith("/\\") &&
            returnPath.all { it in ALLOWED_RETURN_PATH_CHARS } &&
            hasWellFormedEscapes(returnPath)

    /**
     * `UriComponentsBuilder.encode()` 는 쿼리 값 안의 `:`, `/` 를 RFC 3986 상 쿼리에 허용된 문자로 보고
     * 그대로 둔다. `redirect_uri` 값 자체가 URL 이라 이대로 두면 파라미터 경계가 무너진다.
     * `URLEncoder` 로 값을 완전히 인코딩한 뒤 `build(true)` 로 재인코딩을 막는다.
     */
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** `%` 뒤에 16진수 두 자리가 없으면 URL 조립 단계에서 예외가 난다. 400 으로 먼저 드러낸다. */
    private fun hasWellFormedEscapes(value: String): Boolean =
        value.withIndex().none { (index, char) ->
            char == '%' &&
                (
                    index + PERCENT_ESCAPE_LENGTH > value.length ||
                        !value.substring(index + 1, index + PERCENT_ESCAPE_LENGTH).all { it.isHexDigit() }
                )
        }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun normalizeOrigin(value: String): String {
        val uri =
            runCatching { URI(value.trim()) }
                .getOrElse { throw IllegalStateException("app.web-base-url 을 URL 로 읽을 수 없습니다: $value", it) }
        check(uri.isAbsolute && uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "app.web-base-url 은 http(s) 절대 주소여야 합니다 (현재: $value)"
        }
        check(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "app.web-base-url 에는 인증정보·쿼리·프래그먼트가 없어야 합니다 (현재: $value)"
        }
        return value.trim().trimEnd('/')
    }
}
