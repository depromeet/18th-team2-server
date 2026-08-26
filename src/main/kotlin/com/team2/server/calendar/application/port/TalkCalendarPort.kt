package com.team2.server.calendar.application.port

import com.team2.server.calendar.domain.vo.CalendarEvent

interface TalkCalendarPort {
    /** 일정을 새로 만들고 외부 일정 ID 를 반환한다. */
    fun createEvent(
        accessToken: String,
        event: CalendarEvent,
    ): String

    /**
     * 기존 일정을 갱신한다.
     *
     * @return 갱신 성공이면 true, 외부에 해당 일정이 없으면 false
     */
    fun updateEvent(
        accessToken: String,
        eventId: String,
        event: CalendarEvent,
    ): Boolean
}
