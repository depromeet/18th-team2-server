package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.policy.PartyCalendarEventPolicy
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 비회원이 동의 한 번으로 파티 일정을 등록한다.
 *
 * 액세스 토큰을 저장하지 않으므로 갱신 경로가 없다. 같은 사람이 다시 요청하면 카카오를 부르지 않고
 * 이미 등록했다고 답한다 — 부르면 캘린더에 같은 일정이 하나 더 쌓인다.
 */
@Service
class RegisterGuestTalkCalendarEventUseCase(
    private val partyCalendarInfoPort: PartyCalendarInfoPort,
    private val calendarRegistrationService: CalendarRegistrationService,
    private val talkCalendarPort: TalkCalendarPort,
    private val clock: Clock,
) {
    @Suppress("ReturnCount")
    @Transactional
    operator fun invoke(
        inviteToken: String,
        credentials: KakaoConsentCredentials,
    ): GuestCalendarRegistrationResult {
        val now = LocalDateTime.now(clock)
        val info = loadInfoOrNull(inviteToken, now) ?: return GuestCalendarRegistrationResult.INVITE_INVALID

        if (!now.isBefore(info.startedAt)) {
            return GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED
        }
        if (calendarRegistrationService.existsForGuest(credentials.kakaoProviderId, info.partyId)) {
            return GuestCalendarRegistrationResult.ALREADY_REGISTERED
        }

        val registration =
            try {
                calendarRegistrationService.reserveForGuest(credentials.kakaoProviderId, info.partyId)
            } catch (e: BusinessException) {
                if (e.errorCode == ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS) {
                    return GuestCalendarRegistrationResult.ALREADY_REGISTERED
                }
                throw e
            }

        val event =
            PartyCalendarEventPolicy.compose(
                kind = info.celebrationKind,
                celebrantName = info.celebrantName,
                startedAt = info.startedAt,
                inviteUrl = info.inviteUrl,
            )
        val eventId = talkCalendarPort.createEvent(credentials.accessToken, event)
        calendarRegistrationService.linkEvent(registration, eventId)
        return GuestCalendarRegistrationResult.REGISTERED
    }

    /** 초대가 없거나 만료됐을 때만 null 로 흡수한다. 그 외 BusinessException 은 그대로 전파한다. */
    private fun loadInfoOrNull(
        inviteToken: String,
        now: LocalDateTime,
    ) = try {
        partyCalendarInfoPort.loadForInviteToken(inviteToken, now)
    } catch (e: BusinessException) {
        if (e.errorCode == ErrorCode.PARTY_NOT_FOUND || e.errorCode == ErrorCode.INVITE_LINK_EXPIRED) {
            null
        } else {
            throw e
        }
    }
}
