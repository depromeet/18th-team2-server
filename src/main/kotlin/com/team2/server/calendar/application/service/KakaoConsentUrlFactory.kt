package com.team2.server.calendar.application.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val CONSENT_ENTRY_PATH = "/api/v1/kakao-calendar/consent"
const val CONSENT_CALLBACK_PATH = "/api/v1/kakao-calendar/consent/callback"

/** 일정 생성·조회·편집 권한. `talk_calendar_task` 는 할 일 전용이라 쓰지 않는다. */
const val TALK_CALENDAR_SCOPE = "talk_calendar"

@Service
class KakaoConsentUrlFactory(
    private val apiBaseUrl: String,
    private val authBaseUrl: String,
    private val clientId: String,
) {
    @Autowired
    constructor(
        @Value("\${app.api-base-url}") apiBaseUrl: String,
        @Value("\${kakao.auth.base-url:https://kauth.kakao.com}") authBaseUrl: String,
        clientRegistrationRepository: ClientRegistrationRepository,
    ) : this(
        apiBaseUrl = apiBaseUrl,
        authBaseUrl = authBaseUrl,
        clientId = clientRegistrationRepository.findByRegistrationId("kakao").clientId,
    )

    /** 클라이언트가 브라우저를 보낼 주소. 카카오 주소가 아니라 우리 진입점이다. */
    fun consentEntryUrl(
        ticket: String,
        redirectUri: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(apiBaseUrl + CONSENT_ENTRY_PATH)
            .queryParam("ticket", encode(ticket))
            .queryParam("redirect_uri", encode(redirectUri))
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

    /**
     * `UriComponentsBuilder.encode()` 는 쿼리 값 안의 `:`, `/` 를 RFC 3986 상 쿼리에 허용된 문자로 보고
     * 그대로 둔다. `redirect_uri` 값 자체가 URL 이라 이대로 두면 파라미터 경계가 무너진다.
     * `URLEncoder` 로 값을 완전히 인코딩한 뒤 `build(true)` 로 재인코딩을 막는다.
     */
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
