package com.team2.server.calendar.api

import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.application.usecase.ConsentOutcome
import com.team2.server.calendar.application.usecase.SaveKakaoCalendarConsentUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

private const val RESULT_PARAM = "calendarConsent"

/**
 * 브라우저 내비게이션으로 오가는 두 경로다. 서비스 JWT 가 실리지 않으므로 `SecurityConfig` 에서
 * 인증 예외로 두되, 진입은 서명된 티켓과 `redirect_uri` 화이트리스트로, 콜백은 쿠키 티켓과 `state`
 * 대조로 보호한다.
 */
@RestController
@RequestMapping("/api/v1/kakao-calendar/consent")
class KakaoCalendarConsentController(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
    private val saveKakaoCalendarConsentUseCase: SaveKakaoCalendarConsentUseCase,
    private val oAuth2Properties: OAuth2Properties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실패 사유별로 다른 결과를 돌려보내야 해 가드 절이 여러 개다. 합치면 사유 구분이 사라진다. */
    @Suppress("ReturnCount")
    @GetMapping
    fun enter(
        @RequestParam ticket: String,
        @RequestParam("redirect_uri") redirectUri: String,
        response: HttpServletResponse,
    ): RedirectView {
        if (consentTicketSigner.verify(ticket) == null) {
            return RedirectView(resultUrl(fallbackRedirectUri(), ConsentOutcome.EXPIRED))
        }
        if (!oAuth2Properties.authorizedRedirectUris.contains(redirectUri)) {
            return RedirectView(resultUrl(fallbackRedirectUri(), ConsentOutcome.FAILED))
        }
        KakaoCalendarConsentCookies.write(response, ticket, redirectUri, oAuth2Properties.cookieSecure)
        return RedirectView(kakaoConsentUrlFactory.kakaoAuthorizeUrl(ticket))
    }

    /** 실패 사유별로 다른 결과를 돌려보내야 해 가드 절이 여러 개다. 합치면 사유 구분이 사라진다. */
    @Suppress("ReturnCount")
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        val redirectUri = validatedRedirectUri(KakaoCalendarConsentCookies.readRedirectUri(request))
        val cookieTicket = KakaoCalendarConsentCookies.readTicket(request)
        KakaoCalendarConsentCookies.clear(response, oAuth2Properties.cookieSecure)

        if (code.isNullOrBlank()) {
            return RedirectView(resultUrl(redirectUri, ConsentOutcome.DENIED))
        }
        if (cookieTicket == null || state == null || cookieTicket != state) {
            return RedirectView(resultUrl(redirectUri, ConsentOutcome.EXPIRED))
        }
        val userId =
            consentTicketSigner.verify(cookieTicket)
                ?: return RedirectView(resultUrl(redirectUri, ConsentOutcome.EXPIRED))

        val outcome =
            runCatching { saveKakaoCalendarConsentUseCase(code, userId, kakaoConsentUrlFactory.callbackUri()) }
                .onFailure { log.error("카카오 캘린더 동의 저장 실패", it) }
                .getOrDefault(ConsentOutcome.FAILED)
        return RedirectView(resultUrl(redirectUri, outcome))
    }

    /**
     * 쿠키 값은 무결성이 보장되지 않으므로 진입 때와 같은 화이트리스트를 다시 통과시킨다.
     * 통과하지 못하면 기본값으로 돌려보낸다.
     */
    private fun validatedRedirectUri(cookieValue: String?): String =
        cookieValue?.takeIf { oAuth2Properties.authorizedRedirectUris.contains(it) } ?: fallbackRedirectUri()

    /** 복귀 주소를 알 수 없을 때 쓸 기본값. 화이트리스트의 첫 항목이다(`@NotEmpty` 로 기동 시 보장된다). */
    private fun fallbackRedirectUri(): String = oAuth2Properties.authorizedRedirectUris.first()

    private fun resultUrl(
        redirectUri: String,
        outcome: ConsentOutcome,
    ): String =
        UriComponentsBuilder
            .fromUriString(redirectUri)
            .queryParam(RESULT_PARAM, outcome.name.lowercase())
            .encode()
            .toUriString()
}
