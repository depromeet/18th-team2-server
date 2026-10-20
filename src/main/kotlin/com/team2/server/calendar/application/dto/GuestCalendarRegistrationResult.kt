package com.team2.server.calendar.application.dto

/**
 * 비회원 등록 결과. 호출자가 리다이렉트 파라미터로 사유를 전달하므로 예외가 아니라 값으로 돌려준다.
 */
enum class GuestCalendarRegistrationResult {
    REGISTERED,
    ALREADY_REGISTERED,
    PARTY_ALREADY_STARTED,
    INVITE_INVALID,
    FAILED,
}
