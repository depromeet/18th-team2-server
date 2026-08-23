package com.team2.server.calendar.domain.policy

import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.calendar.domain.vo.CelebrationKind
import java.time.LocalDateTime

object PartyCalendarEventPolicy {
    const val DURATION_MINUTES = 30L

    /** 카카오 톡캘린더 일정 제목 제한. */
    const val MAX_TITLE_LENGTH = 50

    /**
     * 미리 알림 시점(분).
     *
     * 카카오는 `0 < 값 ≤ 43200` 만 받으므로 시작 시각 알림(0)은 지정할 수 없다.
     * 파티가 [DURATION_MINUTES] 분짜리라 한 번이면 충분하다.
     */
    const val REMINDER_MINUTES_BEFORE = 5

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
            reminderMinutes = listOf(REMINDER_MINUTES_BEFORE),
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
        return title.truncateToTitleLimit()
    }

    /**
     * 카카오 제목 제한에 맞춰 자른다.
     * 경계가 이모지 한가운데면 짝 없는 상위 서로게이트가 남아 직렬화가 깨지므로 그 한 글자를 더 버린다.
     */
    private fun String.truncateToTitleLimit(): String {
        if (length <= MAX_TITLE_LENGTH) return this
        val cut = take(MAX_TITLE_LENGTH)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }
}
