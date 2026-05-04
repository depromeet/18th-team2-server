package com.team2.server.party.entity

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PartyStatusTest {
    private val birthday: LocalDate = LocalDate.of(2026, 5, 10)
    private val createdAt: LocalDateTime = birthday.minusDays(3).atStartOfDay()
    private val liveStart: LocalDateTime = birthday.atTime(23, 0)

    private fun paperParty(startedAt: LocalDateTime = birthday.atStartOfDay()): PaperOnlyParty {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = startedAt)
        setCreatedAt(party, createdAt)
        return party
    }

    private fun realtimeParty(startedAt: LocalDateTime = liveStart): RealtimeParty {
        val party = RealtimeParty(ownerId = 1L, startedAt = startedAt)
        setCreatedAt(party, createdAt)
        return party
    }

    private fun setCreatedAt(
        entity: Any,
        value: LocalDateTime,
    ) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField("createdAt")
                f.isAccessible = true
                f.set(entity, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    // --- PaperOnlyParty ---

    @Test
    fun `PaperOnlyParty - startedAt 이전은 READY`() {
        val party = paperParty()
        val now = birthday.atStartOfDay().minusMinutes(1)
        assertEquals(PaperOnlyPartyStatus.READY, party.status(now))
    }

    @Test
    fun `PaperOnlyParty - startedAt 시각은 OPEN`() {
        val party = paperParty()
        val now = birthday.atStartOfDay()
        assertEquals(PaperOnlyPartyStatus.OPEN, party.status(now))
    }

    @Test
    fun `PaperOnlyParty - 생성일 +7일 직전은 OPEN`() {
        val party = paperParty()
        val now = createdAt.plusDays(7).minusMinutes(1)
        assertEquals(PaperOnlyPartyStatus.OPEN, party.status(now))
    }

    @Test
    fun `PaperOnlyParty - 생성일 +7일 이후는 CLOSED`() {
        val party = paperParty()
        val now = createdAt.plusDays(7)
        assertEquals(PaperOnlyPartyStatus.CLOSED, party.status(now))
    }

    // --- RealtimeParty ---

    @Test
    fun `RealtimeParty - 라이브 시작 전은 ROLLING_PAPER_OPEN`() {
        val party = realtimeParty()
        val now = liveStart.minusMinutes(1)
        assertEquals(RealtimePartyStatus.ROLLING_PAPER_OPEN, party.status(now))
    }

    @Test
    fun `RealtimeParty - 라이브 시작 시각은 LIVE_OPEN`() {
        val party = realtimeParty()
        assertEquals(RealtimePartyStatus.LIVE_OPEN, party.status(liveStart))
    }

    @Test
    fun `RealtimeParty - 라이브 시작 +9분은 LIVE_OPEN`() {
        val party = realtimeParty()
        val now = liveStart.plusMinutes(9)
        assertEquals(RealtimePartyStatus.LIVE_OPEN, party.status(now))
    }

    @Test
    fun `RealtimeParty - 라이브 종료(+10분) 시각은 LIVE_CLOSED`() {
        val party = realtimeParty()
        val now = liveStart.plusMinutes(10)
        assertEquals(RealtimePartyStatus.LIVE_CLOSED, party.status(now))
    }

    @Test
    fun `RealtimeParty - 생성일 +7일 이후는 ROLLING_PAPER_CLOSED`() {
        val party = realtimeParty()
        val now = createdAt.plusDays(7)
        assertEquals(RealtimePartyStatus.ROLLING_PAPER_CLOSED, party.status(now))
    }
}
