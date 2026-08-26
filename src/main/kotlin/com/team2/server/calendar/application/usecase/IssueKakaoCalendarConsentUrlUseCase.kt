package com.team2.server.calendar.application.usecase

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
) {
    /**
     * 복귀 origin 은 서버 설정이고 클라이언트는 경로만 넘긴다. 형식이 어긋난 경로는 발급 시점에
     * 400 으로 드러낸다. 통과시키면 한참 뒤 동의 진입에서야 `calendarConsent=failed` 로 실패하고
     * 서버 로그도 남지 않는다.
     */
    operator fun invoke(
        userId: Long,
        returnPath: String,
    ): KakaoCalendarConsentUrlResult {
        if (!kakaoConsentUrlFactory.isValidReturnPath(returnPath)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        return KakaoCalendarConsentUrlResult(
            consentUrl = kakaoConsentUrlFactory.consentEntryUrl(consentTicketSigner.issue(userId), returnPath),
        )
    }
}
