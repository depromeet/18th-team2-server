package com.team2.server.party.domain.entity

import org.junit.jupiter.api.Test
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
}
