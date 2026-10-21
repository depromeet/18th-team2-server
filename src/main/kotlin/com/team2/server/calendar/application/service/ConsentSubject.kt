package com.team2.server.calendar.application.service

/**
 * 동의 플로우를 시작한 주체.
 *
 * 회원은 사용자 번호로, 비회원은 초대 토큰으로 식별한다. 비회원은 서비스 사용자 행이 없어
 * 사용자 번호를 실을 수 없고, 콜백에서 어느 파티에 등록할지 알아야 하므로 초대 토큰을 나른다.
 */
sealed interface ConsentSubject {
    data class Member(
        val userId: Long,
    ) : ConsentSubject

    data class Guest(
        val inviteToken: String,
    ) : ConsentSubject
}
