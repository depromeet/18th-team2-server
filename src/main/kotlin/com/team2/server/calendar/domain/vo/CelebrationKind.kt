package com.team2.server.calendar.domain.vo

/**
 * 캘린더 일정 제목에 쓰는 파티 종류.
 *
 * party feature 의 PartyPurpose 를 그대로 쓰지 않는 이유는 calendar.domain 이
 * 다른 feature 에 의존하지 않게 하기 위함이다. 매핑은 infrastructure 어댑터가 한다.
 */
enum class CelebrationKind(
    val partyLabel: String,
) {
    BIRTHDAY("생일 파티"),
    JOB_CHANGE("이직 축하 파티"),
    WEDDING("결혼 축하 파티"),
}
