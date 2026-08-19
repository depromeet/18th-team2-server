package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import org.springframework.stereotype.Service

@Service
class IssueKakaoCalendarConsentUrlUseCase(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
) {
    operator fun invoke(
        userId: Long,
        redirectUri: String,
    ): KakaoCalendarConsentUrlResult =
        KakaoCalendarConsentUrlResult(
            consentUrl = kakaoConsentUrlFactory.consentEntryUrl(consentTicketSigner.issue(userId), redirectUri),
        )
}
