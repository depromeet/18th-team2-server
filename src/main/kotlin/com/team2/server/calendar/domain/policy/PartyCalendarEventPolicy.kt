package com.team2.server.calendar.domain.policy

import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.calendar.domain.vo.CelebrationKind
import java.time.LocalDateTime

object PartyCalendarEventPolicy {
    const val DURATION_MINUTES = 30L

    /** 카카오 톡캘린더 일정 제목 제한. */
    const val MAX_TITLE_LENGTH = 50

    fun compose(
        kind: CelebrationKind,
        celebrantName: String?,
        startedAt: LocalDateTime,
        inviteUrl: String?,
    ): CalendarEvent =
        CalendarEvent(
            title = composeTitle(kind, celebrantName),
            startAt = startedAt,
            endAt = startedAt.plusMinutes(DURATION_MINUTES),
            description = inviteUrl?.let { "초대 링크: $it" } ?: "",
        )

    private fun composeTitle(
        kind: CelebrationKind,
        celebrantName: String?,
    ): String {
        val title =
            if (celebrantName.isNullOrBlank()) {
                kind.partyLabel
            } else {
                "${celebrantName.trim()}님의 ${kind.partyLabel}"
            }
        return title.take(MAX_TITLE_LENGTH)
    }
}
