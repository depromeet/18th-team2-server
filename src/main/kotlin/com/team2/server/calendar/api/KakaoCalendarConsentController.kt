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
private const val FALLBACK_RETURN_PATH = "/"

/**
 * 브라우저 내비게이션으로 오가는 두 경로다. 서비스 JWT 가 실리지 않으므로 `SecurityConfig` 에서
 * 인증 예외로 두되, 진입은 서명된 티켓과 `return_path` 형식 검증으로, 콜백은 쿠키 티켓과 `state`
 * 대조로 보호한다. 복귀 origin 은 서버 설정이라 클라이언트가 호스트를 지정할 방법이 없다.
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
        @RequestParam("return_path") returnPath: String,
        response: HttpServletResponse,
    ): RedirectView {
        if (consentTicketSigner.verify(ticket) == null) {
            return RedirectView(resultUrl(FALLBACK_RETURN_PATH, ConsentOutcome.EXPIRED))
        }
        if (!kakaoConsentUrlFactory.isValidReturnPath(returnPath)) {
            return RedirectView(resultUrl(FALLBACK_RETURN_PATH, ConsentOutcome.FAILED))
        }
        KakaoCalendarConsentCookies.write(response, ticket, returnPath, oAuth2Properties.cookieSecure)
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
        val returnPath = validatedReturnPath(KakaoCalendarConsentCookies.readReturnPath(request))
        val cookieTicket = KakaoCalendarConsentCookies.readTicket(request)
        KakaoCalendarConsentCookies.clear(response, oAuth2Properties.cookieSecure)

        if (code.isNullOrBlank()) {
            return RedirectView(resultUrl(returnPath, ConsentOutcome.DENIED))
        }
        if (cookieTicket == null || state == null || cookieTicket != state) {
            return RedirectView(resultUrl(returnPath, ConsentOutcome.EXPIRED))
        }
        val userId =
            consentTicketSigner.verify(cookieTicket)
                ?: return RedirectView(resultUrl(returnPath, ConsentOutcome.EXPIRED))

        val outcome =
            runCatching { saveKakaoCalendarConsentUseCase(code, userId, kakaoConsentUrlFactory.callbackUri()) }
                .onFailure { log.error("카카오 캘린더 동의 저장 실패", it) }
                .getOrDefault(ConsentOutcome.FAILED)
        return RedirectView(resultUrl(returnPath, outcome))
    }

    /** 쿠키 값은 무결성이 보장되지 않으므로 진입 때와 같은 검증을 다시 통과시킨다. */
    private fun validatedReturnPath(cookieValue: String?): String =
        cookieValue?.takeIf { kakaoConsentUrlFactory.isValidReturnPath(it) } ?: FALLBACK_RETURN_PATH

    private fun resultUrl(
        returnPath: String,
        outcome: ConsentOutcome,
    ): String =
        UriComponentsBuilder
            .fromUriString(kakaoConsentUrlFactory.returnUrl(returnPath))
            .queryParam(RESULT_PARAM, outcome.name.lowercase())
            .build(true)
            .toUriString()
}
