package com.team2.server.calendar.application.port

/** 카카오 회원번호로 우리 사용자를 찾는다. 없으면 null. */
interface CalendarUserPort {
    fun findUserIdByKakaoProviderId(providerId: String): Long?
}
