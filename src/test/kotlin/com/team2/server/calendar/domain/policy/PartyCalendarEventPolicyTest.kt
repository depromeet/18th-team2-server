package com.team2.server.calendar.domain.policy

import com.team2.server.calendar.domain.vo.CelebrationKind
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyCalendarEventPolicyTest {
    private val startedAt = LocalDateTime.of(2026, 8, 20, 19, 0)

    @Test
    fun `주인공 이름으로 제목을 만든다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("지민님의 생일 파티", event.title)
    }

    @Test
    fun `파티 목적에 따라 제목 문구가 달라진다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.WEDDING,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("지민님의 결혼 축하 파티", event.title)
    }

    @Test
    fun `주인공 이름이 없으면 파티 종류만 제목으로 쓴다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.JOB_CHANGE,
                celebrantName = "  ",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("이직 축하 파티", event.title)
    }

    @Test
    fun `제목은 50자를 넘지 않는다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "가".repeat(80),
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals(50, event.title.length)
        assertTrue(event.title.startsWith("가가가"))
    }

    @Test
    fun `종료 시각은 시작 30분 뒤다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals(startedAt, event.startAt)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 30), event.endAt)
    }

    @Test
    fun `초대 링크가 있으면 설명에 넣는다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = "https://example.com/invite/abc",
            )

        assertEquals("초대 링크: https://example.com/invite/abc", event.description)
    }

    @Test
    fun `초대 링크가 없으면 설명은 빈 문자열이다`() {
        val event =
            PartyCalendarEventPolicy.compose(
                kind = CelebrationKind.BIRTHDAY,
                celebrantName = "지민",
                startedAt = startedAt,
                inviteUrl = null,
            )

        assertEquals("", event.description)
    }
}
