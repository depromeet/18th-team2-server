package com.team2.server.calendar.application.port

import com.team2.server.calendar.domain.vo.CelebrationKind
import java.time.LocalDateTime

interface PartyCalendarInfoPort {
    /**
     * 캘린더 등록에 필요한 파티 정보를 읽는다.
     * 요청자가 파티의 호스트도 현재 참여자도 아니면 PARTY_FORBIDDEN 을 던진다.
     */
    fun loadForMember(
        partyId: Long,
        userId: Long,
        now: LocalDateTime,
    ): PartyCalendarInfo

    /**
     * 초대 토큰으로 파티 정보를 읽는다. 참여자 여부는 보지 않는다 — 초대 링크 보유가 권한 근거다.
     */
    fun loadForInviteToken(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyCalendarInfo
}

data class PartyCalendarInfo(
    val partyId: Long,
    val celebrationKind: CelebrationKind,
    val celebrantName: String?,
    val startedAt: LocalDateTime,
    val inviteUrl: String?,
)
