package com.team2.server.calendar.application.usecase

import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service

@Service
class IssueKakaoCalendarConsentUrlUseCase(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
    private val oAuth2Properties: OAuth2Properties,
) {
    /**
     * 화이트리스트 밖 redirectUri 는 발급 시점에 400 으로 드러낸다. 그냥 통과시키면 한참 뒤
     * 동의 진입 리다이렉트에서야 `calendarConsent=failed` 로 실패하고 서버 로그도 남지 않는다.
     */
    operator fun invoke(
        userId: Long,
        redirectUri: String,
    ): KakaoCalendarConsentUrlResult {
        if (!oAuth2Properties.authorizedRedirectUris.contains(redirectUri)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        return KakaoCalendarConsentUrlResult(
            consentUrl = kakaoConsentUrlFactory.consentEntryUrl(consentTicketSigner.issue(userId), redirectUri),
        )
    }
}
