package com.team2.server.calendar.domain.vo

import java.time.LocalDateTime

data class CalendarEvent(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val description: String,
)
