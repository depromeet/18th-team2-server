package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.port.PartyCalendarInfo
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.application.service.CalendarRegistrationService
import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterPartyTalkCalendarEventUseCaseTest {
    private val partyCalendarInfoPort: PartyCalendarInfoPort = mock()
    private val talkCalendarPort: TalkCalendarPort = mock()
    private val calendarRegistrationService: CalendarRegistrationService = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 18, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        RegisterPartyTalkCalendarEventUseCase(
            partyCalendarInfoPort,
            talkCalendarPort,
            calendarRegistrationService,
            clock,
        )

    private val command =
        RegisterPartyTalkCalendarEventCommand(partyId = 1L, userId = 10L, kakaoAccessToken = "kakao-token")

    private fun partyInfo(startedAt: LocalDateTime = LocalDateTime.of(2026, 8, 20, 19, 0)) =
        PartyCalendarInfo(
            partyId = 1L,
            celebrationKind = CelebrationKind.BIRTHDAY,
            celebrantName = "지민",
            startedAt = startedAt,
            inviteUrl = "https://example.com/invite/abc",
        )

    private fun registration(eventId: String?) =
        CalendarRegistration(
            userId = 10L,
            partyId = 1L,
            provider = CalendarProvider.KAKAO_TALK,
            eventId = eventId,
        )

    @Test
    fun `이력이 없으면 행을 먼저 확보하고 일정을 생성한다`() {
        val reserved = registration(null)
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(reserved)
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        val result = useCase(command)

        assertEquals("event-1", result.eventId)
        assertFalse(result.updated)
        verify(calendarRegistrationService).linkEvent(reserved, "event-1")
    }

    @Test
    fun `생성되는 일정은 정책이 만든 제목과 30분 길이를 갖는다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(registration(null))
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        useCase(command)

        val captor = argumentCaptor<CalendarEvent>()
        verify(talkCalendarPort).createEvent(eq("kakao-token"), captor.capture())
        assertEquals("지민님의 생일 파티", captor.firstValue.title)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), captor.firstValue.startAt)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 30), captor.firstValue.endAt)
        assertEquals("초대 링크: https://example.com/invite/abc", captor.firstValue.description)
    }

    @Test
    fun `이력이 있으면 기존 일정을 갱신한다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(registration("event-1"))
        whenever(talkCalendarPort.updateEvent(eq("kakao-token"), eq("event-1"), any())).thenReturn(true)

        val result = useCase(command)

        assertEquals("event-1", result.eventId)
        assertTrue(result.updated)
        verify(talkCalendarPort, never()).createEvent(any(), any())
        verify(calendarRegistrationService, never()).reserve(any(), any())
    }

    @Test
    fun `카카오에 일정이 없으면 같은 이력으로 다시 생성한다`() {
        val existing = registration("event-1")
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow)).thenReturn(partyInfo())
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(existing)
        whenever(talkCalendarPort.updateEvent(eq("kakao-token"), eq("event-1"), any())).thenReturn(false)
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-2")

        val result = useCase(command)

        assertEquals("event-2", result.eventId)
        assertFalse(result.updated)
        verify(calendarRegistrationService).linkEvent(existing, "event-2")
        verify(calendarRegistrationService, never()).reserve(any(), any())
    }

    @Test
    fun `이미 시작된 파티는 등록할 수 없다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow))
            .thenReturn(partyInfo(startedAt = fixedNow))

        val exception = kotlin.runCatching { useCase(command) }.exceptionOrNull()

        assertEquals(
            ErrorCode.TALK_CALENDAR_PARTY_ALREADY_STARTED,
            (exception as BusinessException).errorCode,
        )
        verify(talkCalendarPort, never()).createEvent(any(), any())
    }

    @Test
    fun `시작 1분 전이면 등록할 수 있다`() {
        whenever(partyCalendarInfoPort.loadForMember(1L, 10L, fixedNow))
            .thenReturn(partyInfo(startedAt = fixedNow.plusMinutes(1)))
        whenever(calendarRegistrationService.find(10L, 1L)).thenReturn(null)
        whenever(calendarRegistrationService.reserve(10L, 1L)).thenReturn(registration(null))
        whenever(talkCalendarPort.createEvent(eq("kakao-token"), any())).thenReturn("event-1")

        assertEquals("event-1", useCase(command).eventId)
    }
}
