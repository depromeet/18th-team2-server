package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.policy.PartyCalendarEventPolicy
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RegisterPartyTalkCalendarEventUseCase(
    private val partyCalendarInfoPort: PartyCalendarInfoPort,
    private val talkCalendarPort: TalkCalendarPort,
    private val calendarRegistrationService: CalendarRegistrationService,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(command: RegisterPartyTalkCalendarEventCommand): RegisterPartyTalkCalendarEventResult {
        val now = LocalDateTime.now(clock)
        val info = partyCalendarInfoPort.loadForMember(command.partyId, command.userId, now)
        if (!now.isBefore(info.startedAt)) {
            throw BusinessException(ErrorCode.TALK_CALENDAR_PARTY_ALREADY_STARTED)
        }

        val event =
            PartyCalendarEventPolicy.compose(
                kind = info.celebrationKind,
                celebrantName = info.celebrantName,
                startedAt = info.startedAt,
                inviteUrl = info.inviteUrl,
            )
        val existing = calendarRegistrationService.find(command.userId, command.partyId)
        val existingEventId = existing?.eventId

        if (existing != null &&
            existingEventId != null &&
            talkCalendarPort.updateEvent(command.kakaoAccessToken, existingEventId, event)
        ) {
            return RegisterPartyTalkCalendarEventResult(eventId = existingEventId, updated = true)
        }

        // 이력이 없거나, 사용자가 카카오에서 일정을 지워 갱신할 대상이 사라진 경우
        val registration = existing ?: calendarRegistrationService.reserve(command.userId, command.partyId)
        return RegisterPartyTalkCalendarEventResult(
            eventId = createEvent(command.kakaoAccessToken, event, registration),
            updated = false,
        )
    }

    private fun createEvent(
        accessToken: String,
        event: CalendarEvent,
        registration: CalendarRegistration,
    ): String {
        val eventId = talkCalendarPort.createEvent(accessToken, event)
        calendarRegistrationService.linkEvent(registration, eventId)
        return eventId
    }
}
