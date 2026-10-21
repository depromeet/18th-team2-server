package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.service.ConsentSubject
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service

@Service
class IssueGuestKakaoCalendarConsentUrlUseCase(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
) {
    /**
     * 초대 토큰의 유효성은 보지 않는다. 콜백이 등록 시점에 확인하고 사유를 돌려주므로
     * 여기서 또 조회하면 같은 일을 두 번 한다.
     */
    operator fun invoke(
        inviteToken: String,
        returnPath: String,
    ): KakaoCalendarConsentUrlResult {
        if (!kakaoConsentUrlFactory.isValidReturnPath(returnPath)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        return KakaoCalendarConsentUrlResult(
            consentUrl =
                kakaoConsentUrlFactory.consentEntryUrl(
                    consentTicketSigner.issue(ConsentSubject.Guest(inviteToken)),
                    returnPath,
                ),
        )
    }
}
