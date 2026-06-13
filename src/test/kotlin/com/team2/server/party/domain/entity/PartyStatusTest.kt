package com.team2.server.party.domain.entity

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
    fun `PaperOnlyParty - 시작일 +7일 직전은 OPEN`() {
        val party = paperParty()
        val now = birthday.atStartOfDay().plusDays(7).minusMinutes(1)
        assertEquals(PaperOnlyPartyStatus.OPEN, party.status(now))
    }

    @Test
    fun `PaperOnlyParty - 시작일 +7일 이후는 CLOSED`() {
        val party = paperParty()
        val now = birthday.atStartOfDay().plusDays(7)
        assertEquals(PaperOnlyPartyStatus.CLOSED, party.status(now))
    }

    @Test
    fun `PaperOnlyParty - 주최자 롤링페이퍼 열람 시각은 시작일 오후 10시`() {
        val party = paperParty(startedAt = birthday.atStartOfDay())
        val hostViewableAt = birthday.atTime(22, 0)
        assertEquals(hostViewableAt, party.hostViewableAt())
        assertEquals(false, party.canHostViewRollingPapers(hostViewableAt.minusNanos(1)))
        assertEquals(true, party.canHostViewRollingPapers(hostViewableAt))
    }

    @Test
    fun `PaperOnlyParty - 시작 시각이 오후 10시 이후이면 다음날 오후 10시에 주최자 열람 가능`() {
        val party = paperParty(startedAt = birthday.atTime(23, 30))
        val hostViewableAt = birthday.plusDays(1).atTime(22, 0)
        assertEquals(hostViewableAt, party.hostViewableAt())
        assertEquals(false, party.canHostViewRollingPapers(hostViewableAt.minusNanos(1)))
        assertEquals(true, party.canHostViewRollingPapers(hostViewableAt))
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
    fun `RealtimeParty - 라이브 종료 시작(+10분) 시각은 LIVE_ENDING`() {
        val party = realtimeParty()
        val now = liveStart.plusMinutes(10)
        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(now))
    }

    @Test
    fun `RealtimeParty - 종료 카운트다운 60초 이후는 LIVE_CLOSED`() {
        val party = realtimeParty()
        val now = liveStart.plusMinutes(10).plusSeconds(60)
        assertEquals(RealtimePartyStatus.LIVE_CLOSED, party.status(now))
    }

    @Test
    fun `RealtimeParty - 라이브 시작 +7일 이후는 ROLLING_PAPER_CLOSED`() {
        val party = realtimeParty()
        val now = liveStart.plusDays(7)
        assertEquals(RealtimePartyStatus.ROLLING_PAPER_CLOSED, party.status(now))
    }

    @Test
    fun `RealtimeParty - 주최자 롤링페이퍼 열람 시각은 라이브 종료 시각`() {
        val party = realtimeParty()
        val hostViewableAt = liveStart.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
        assertEquals(hostViewableAt, party.hostViewableAt())
        assertEquals(false, party.canHostViewRollingPapers(hostViewableAt.minusNanos(1)))
        assertEquals(true, party.canHostViewRollingPapers(hostViewableAt))
    }

    @Test
    fun `RealtimeParty - 수동 종료 시작 시 주최자 롤링페이퍼 열람 시각은 종료 시작 시각`() {
        val endingStartedAt = liveStart.plusMinutes(6)
        val party = realtimeParty().apply { liveEndingStartedAt = endingStartedAt }
        assertEquals(endingStartedAt, party.hostViewableAt())
        assertEquals(RealtimePartyEndingReason.HOST_REQUEST, party.endingReason())
        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(endingStartedAt))
        assertEquals(RealtimePartyStatus.LIVE_CLOSED, party.status(endingStartedAt.plusSeconds(60)))
    }

    @Test
    fun `RealtimeParty - 자동 종료 시각부터는 TIME_LIMIT_REACHED`() {
        val party = realtimeParty().apply { liveEndingStartedAt = automaticEndingStartedAt() }

        assertEquals(RealtimePartyEndingReason.TIME_LIMIT_REACHED, party.endingReason())
    }

    @Test
    fun `RealtimeParty - 자동 종료 시각이 지나면 저장 전에도 TIME_LIMIT_REACHED`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyEndingReason.TIME_LIMIT_REACHED, party.endingReason(party.automaticEndingStartedAt()))
    }

    @Test
    fun `RealtimeParty - 주최자 입장 4분 전 수동 종료는 HOST_LEFT`() {
        val hostEnteredAt = liveStart.plusSeconds(10)
        val party = realtimeParty().apply { this.hostEnteredAt = hostEnteredAt }

        assertEquals(
            RealtimePartyEndingReason.HOST_LEFT,
            party.endingReasonForManualRequest(hostEnteredAt.plusMinutes(4).minusNanos(1)),
        )
    }

    @Test
    fun `RealtimeParty - 주최자 입장 정확히 4분 뒤 수동 종료는 HOST_REQUEST`() {
        val hostEnteredAt = liveStart.plusSeconds(10)
        val party = realtimeParty().apply { this.hostEnteredAt = hostEnteredAt }

        assertEquals(
            RealtimePartyEndingReason.HOST_REQUEST,
            party.endingReasonForManualRequest(hostEnteredAt.plusMinutes(4)),
        )
    }

    @Test
    fun `RealtimeParty - 정확히 10분 시점 수동 종료는 TIME_LIMIT_REACHED`() {
        val party = realtimeParty().apply { hostEnteredAt = liveStart }

        assertEquals(
            RealtimePartyEndingReason.TIME_LIMIT_REACHED,
            party.endingReasonForManualRequest(party.automaticEndingStartedAt()),
        )
    }

    @Test
    fun `RealtimeParty - 주최자 입장 4분 전이어도 박터뜨리기가 끝났으면 HOST_REQUEST`() {
        val hostEnteredAt = liveStart
        val requestAt = hostEnteredAt.plusMinutes(2)
        val party =
            realtimeParty().apply {
                this.hostEnteredAt = hostEnteredAt
                burstGameEndedAt = requestAt.minusNanos(1)
            }

        assertEquals(RealtimePartyEndingReason.HOST_REQUEST, party.endingReasonForManualRequest(requestAt))
    }

    @Test
    fun `RealtimeParty - 종료 카운트다운 중에는 종료 인사하기를 사용할 수 없다`() {
        val endingStartedAt = liveStart.plusMinutes(5)
        val party =
            realtimeParty().apply {
                hostEnteredAt = liveStart
                liveEndingStartedAt = endingStartedAt
                liveEndingReason = RealtimePartyEndingReason.HOST_REQUEST
            }

        assertEquals(false, party.isHostFarewellAvailable(endingStartedAt))
    }

    @Test
    fun `RealtimeParty - Party 종료가 LIVE_CLOSED보다 우선한다`() {
        val now = liveStart.plusDays(7)
        val party = realtimeParty().apply { liveEndingStartedAt = now.minusSeconds(60) }
        assertEquals(RealtimePartyStatus.ROLLING_PAPER_CLOSED, party.status(now))
    }
}
