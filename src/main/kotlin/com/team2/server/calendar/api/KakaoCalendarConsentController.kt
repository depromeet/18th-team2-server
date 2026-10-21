package com.team2.server.calendar.api

import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.application.service.ConsentSubject
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.application.usecase.ConsentOutcome
import com.team2.server.calendar.application.usecase.ExchangeKakaoConsentUseCase
import com.team2.server.calendar.application.usecase.RegisterGuestTalkCalendarEventUseCase
import com.team2.server.calendar.application.usecase.SaveKakaoCalendarConsentUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
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
    private val exchangeKakaoConsentUseCase: ExchangeKakaoConsentUseCase,
    private val registerGuestTalkCalendarEventUseCase: RegisterGuestTalkCalendarEventUseCase,
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
        val subject =
            consentTicketSigner.verify(cookieTicket)
                ?: return RedirectView(resultUrl(returnPath, ConsentOutcome.EXPIRED))

        val outcome =
            runCatching {
                when (subject) {
                    is ConsentSubject.Member ->
                        saveKakaoCalendarConsentUseCase(code, subject.userId, kakaoConsentUrlFactory.callbackUri())
                    is ConsentSubject.Guest -> registerGuest(code, subject.inviteToken)
                }
            }.onFailure { log.error("카카오 캘린더 동의 처리 실패", it) }
                .getOrDefault(ConsentOutcome.FAILED)
        return RedirectView(resultUrl(returnPath, outcome))
    }

    /** 비회원은 토큰을 저장하지 않으므로 여기서 등록까지 끝낸다. 토큰은 반환하지 않고 버린다. */
    private fun registerGuest(
        code: String,
        inviteToken: String,
    ): ConsentOutcome {
        val credentials =
            exchangeKakaoConsentUseCase(code, kakaoConsentUrlFactory.callbackUri())
                ?: return ConsentOutcome.FAILED
        return when (register(inviteToken, credentials)) {
            GuestCalendarRegistrationResult.REGISTERED -> ConsentOutcome.REGISTERED
            GuestCalendarRegistrationResult.ALREADY_REGISTERED -> ConsentOutcome.ALREADY_REGISTERED
            GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED -> ConsentOutcome.PARTY_ALREADY_STARTED
            GuestCalendarRegistrationResult.INVITE_INVALID -> ConsentOutcome.INVITE_INVALID
        }
    }

    /**
     * 동시 요청 경쟁에서 진 쪽을 중복 등록으로 받는다.
     *
     * UseCase 안에서 잡으면 제약 위반으로 rollback-only 가 된 트랜잭션을 정상 반환시켜 커밋이
     * `UnexpectedRollbackException` 으로 깨진다. 그래서 전파받아 트랜잭션이 끝난 뒤 여기서 매핑한다.
     */
    private fun register(
        inviteToken: String,
        credentials: KakaoConsentCredentials,
    ): GuestCalendarRegistrationResult =
        try {
            registerGuestTalkCalendarEventUseCase(inviteToken, credentials)
        } catch (e: BusinessException) {
            if (e.errorCode != ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS) throw e
            GuestCalendarRegistrationResult.ALREADY_REGISTERED
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
