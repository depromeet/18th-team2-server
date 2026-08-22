package com.team2.server.calendar.domain.vo

import java.time.LocalDateTime

private const val REMINDER_MIN_MINUTES = 5
private const val REMINDER_MAX_MINUTES = 43_200
private const val REMINDER_INTERVAL_MINUTES = 5
private const val REMINDER_MAX_COUNT = 2

data class CalendarEvent(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val description: String,
    /** 미리 알림 시점(분). 카카오는 5분 간격으로 최대 2개까지 받는다. */
    val reminderMinutes: List<Int>,
) {
    /**
     * 카카오 제약을 여기서 막는다. 통과시키면 일정 생성 요청이 400 으로 거부되는데,
     * 어댑터가 그것을 `KAKAO_CALENDAR_UNAVAILABLE` 로 바꿔 카카오 장애처럼 보이게 된다.
     */
    init {
        require(reminderMinutes.size <= REMINDER_MAX_COUNT) {
            "미리 알림은 최대 ${REMINDER_MAX_COUNT}개다 (현재 ${reminderMinutes.size}개)"
        }
        require(reminderMinutes.all { it in REMINDER_MIN_MINUTES..REMINDER_MAX_MINUTES }) {
            "미리 알림은 $REMINDER_MIN_MINUTES~$REMINDER_MAX_MINUTES 분이어야 한다 (현재 $reminderMinutes)"
        }
        require(reminderMinutes.all { it % REMINDER_INTERVAL_MINUTES == 0 }) {
            "미리 알림은 ${REMINDER_INTERVAL_MINUTES}분 간격이어야 한다 (현재 $reminderMinutes)"
        }
    }
}
