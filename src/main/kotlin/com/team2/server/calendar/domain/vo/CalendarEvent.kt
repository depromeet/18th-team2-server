package com.team2.server.calendar.domain.vo

import java.time.LocalDateTime

data class CalendarEvent(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val description: String,
    /** 미리 알림 시점(분). 카카오는 5분 간격으로 최대 2개까지 받는다. */
    val reminderMinutes: List<Int>,
)
