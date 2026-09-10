package com.team2.server.party.domain.entity

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyDomainTest {
    private val defaultStartedAt = LocalDateTime.of(2026, 6, 1, 14, 0)

    @Test
    fun `RealtimeParty는 Party의 하위 타입이다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)
        assertIs<Party>(party)
    }

    @Test
    fun `PaperOnlyParty는 Party의 하위 타입이다`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = defaultStartedAt)
        assertIs<Party>(party)
    }

    @Test
    fun `Party 공통 필드는 두 타입 모두에서 접근 가능하다`() {
        val startedAt = LocalDateTime.of(2026, 6, 1, 14, 0)
        val realtime = RealtimeParty(ownerId = 1L, celebrantNickname = "홍길동", startedAt = startedAt)
        val paperOnly = PaperOnlyParty(ownerId = 2L, celebrantNickname = "김철수", startedAt = startedAt)

        assertTrue(realtime.celebrantNickname == "홍길동")
        assertTrue(realtime.startedAt == startedAt)
        assertTrue(paperOnly.celebrantNickname == "김철수")
        assertTrue(paperOnly.startedAt == startedAt)
    }

    @Test
    fun `RealtimeParty는 PaperOnlyParty가 아니다`() {
        val party: Party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)
        assertFalse(party is PaperOnlyParty)
    }

    @Test
    fun `PaperOnlyParty는 RealtimeParty가 아니다`() {
        val party: Party = PaperOnlyParty(ownerId = 1L, startedAt = defaultStartedAt)
        assertFalse(party is RealtimeParty)
    }

    @Test
    fun `Party는 시작 시각 후 7일을 종료 시각으로 가진다`() {
        val createdAt = LocalDateTime.of(2026, 5, 1, 12, 0)
        val startedAt = LocalDateTime.of(2026, 6, 1, 14, 0)
        val party = RealtimeParty(ownerId = 1L, startedAt = startedAt)
        party.createdAt = createdAt

        assertEquals(startedAt.plusDays(Party.ENDED_AFTER_DAYS), party.endedAt())
        assertFalse(party.isEnded(startedAt.plusDays(Party.ENDED_AFTER_DAYS).minusNanos(1)))
        assertTrue(party.isEnded(startedAt.plusDays(Party.ENDED_AFTER_DAYS)))
    }

    @Test
    fun `RealtimeParty는 라이브 시작 전에는 10분 타이머 마감 시각이 없다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)

        assertNull(party.liveDeadlineAt)
    }

    @Test
    fun `RealtimeParty의 10분 타이머 마감 시각은 예약 시각이 아닌 실제 라이브 시작 시각 기준이다`() {
        val liveStartedAt = defaultStartedAt.plusSeconds(23)
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt, liveStartedAt = liveStartedAt)

        assertEquals(liveStartedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES), party.liveDeadlineAt)
    }

    @Test
    fun `RealtimeParty의 입장 가능 시작 시각은 예약 시각 5분 전이다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)

        assertEquals(
            defaultStartedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES),
            party.enterableFrom(),
        )
    }

    @Test
    fun `RealtimeParty는 예약 시각 5분 전부터 입장 가능하다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)
        val enterableFrom = party.enterableFrom()

        assertFalse(party.isEnterable(enterableFrom.minusNanos(1)))
        assertTrue(party.isEnterable(enterableFrom))
        assertTrue(party.isEnterable(defaultStartedAt))
    }

    @Test
    fun `RealtimeParty는 라이브 시작 전이면 시작 유예 마감까지 입장 가능하다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt)
        val startDeadlineAt = party.startDeadlineAt()

        assertTrue(party.isEnterable(startDeadlineAt.minusNanos(1)))
        assertFalse(party.isEnterable(startDeadlineAt))
    }

    @Test
    fun `RealtimeParty는 라이브 시작 후 10분이 지나면 입장 불가하다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = defaultStartedAt, liveStartedAt = defaultStartedAt)
        val liveDeadlineAt = defaultStartedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)

        assertTrue(party.isEnterable(liveDeadlineAt.minusNanos(1)))
        assertFalse(party.isEnterable(liveDeadlineAt))
    }

    @Test
    fun `RealtimeParty가 조기 종료되면 종료 시각부터 입장 불가하다`() {
        val liveEndingStartedAt = defaultStartedAt.plusMinutes(3)
        val party =
            RealtimeParty(
                ownerId = 1L,
                startedAt = defaultStartedAt,
                liveEndingStartedAt = liveEndingStartedAt,
                liveStartedAt = defaultStartedAt,
            )

        assertTrue(party.isEnterable(liveEndingStartedAt.minusNanos(1)))
        assertFalse(party.isEnterable(liveEndingStartedAt))
        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(liveEndingStartedAt))
    }

    // --- 주최자 롤링페이퍼 오픈 안내 ---

    @Test
    fun `PAPER_ONLY 파티는 시작일 22시부터 주최자에게 오픈 안내가 필요하다`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDate.of(2026, 6, 1).atStartOfDay())
        val openAt = LocalDateTime.of(2026, 6, 1, 22, 0)

        assertFalse(party.needsHostRollingPaperNotice(openAt.minusNanos(1)))
        assertTrue(party.needsHostRollingPaperNotice(openAt))
    }

    @Test
    fun `REALTIME 파티는 조기 종료 시각부터 주최자에게 오픈 안내가 필요하다`() {
        val liveEndingStartedAt = defaultStartedAt.plusMinutes(3)
        val party =
            RealtimeParty(
                ownerId = 1L,
                startedAt = defaultStartedAt,
                liveEndingStartedAt = liveEndingStartedAt,
                liveStartedAt = defaultStartedAt,
            )

        assertFalse(party.needsHostRollingPaperNotice(liveEndingStartedAt.minusNanos(1)))
        assertTrue(party.needsHostRollingPaperNotice(liveEndingStartedAt))
    }

    @Test
    fun `오픈 안내를 확인하면 더 이상 안내가 필요하지 않다`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDate.of(2026, 6, 1).atStartOfDay())
        val openAt = LocalDateTime.of(2026, 6, 1, 22, 0)

        party.markHostRollingPaperNoticeSeen(openAt)

        assertEquals(openAt, party.hostRollingPaperNoticeSeenAt)
        assertFalse(party.needsHostRollingPaperNotice(openAt))
    }

    @Test
    fun `오픈 안내 확인은 멱등이며 최초 시각을 유지한다`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDate.of(2026, 6, 1).atStartOfDay())
        val first = LocalDateTime.of(2026, 6, 1, 22, 0)

        party.markHostRollingPaperNoticeSeen(first)
        party.markHostRollingPaperNoticeSeen(first.plusHours(1))

        assertEquals(first, party.hostRollingPaperNoticeSeenAt)
    }
}
