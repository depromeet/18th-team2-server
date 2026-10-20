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
        val party = realtimeParty().apply { liveStartedAt = liveStart }
        val now = liveStart.plusMinutes(9)
        assertEquals(RealtimePartyStatus.LIVE_OPEN, party.status(now))
    }

    @Test
    fun `RealtimeParty - 라이브 종료 시작(+10분) 시각은 LIVE_ENDING`() {
        val party = realtimeParty().apply { liveStartedAt = liveStart }
        val now = liveStart.plusMinutes(10)
        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(now))
    }

    @Test
    fun `RealtimeParty - 종료 카운트다운 30초 이후는 LIVE_CLOSED`() {
        val party = realtimeParty().apply { liveStartedAt = liveStart }
        val now = liveStart.plusMinutes(10).plusSeconds(30)
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
        val party = realtimeParty().apply { liveStartedAt = liveStart }
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
        assertEquals(RealtimePartyStatus.LIVE_CLOSED, party.status(endingStartedAt.plusSeconds(30)))
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
    fun `RealtimeParty - 명시 종료는 시작 직후여도 HOST_REQUEST`() {
        val party = realtimeParty().apply { liveStartedAt = liveStart }

        assertEquals(
            RealtimePartyEndingReason.HOST_REQUEST,
            party.endingReasonForHost(RealtimePartyEndTrigger.HOST_REQUEST, liveStart.plusSeconds(10)),
        )
    }

    @Test
    fun `RealtimeParty - 예약 시각 이후 미시작 파티의 명시 종료는 HOST_REQUEST`() {
        val party = realtimeParty()

        assertEquals(
            RealtimePartyEndingReason.HOST_REQUEST,
            party.endingReasonForHost(RealtimePartyEndTrigger.HOST_REQUEST, liveStart.plusMinutes(3)),
        )
    }

    @Test
    fun `RealtimeParty - 주최자 퇴장은 경과 시간과 무관하게 HOST_LEFT`() {
        val party = realtimeParty().apply { liveStartedAt = liveStart }

        assertEquals(
            RealtimePartyEndingReason.HOST_LEFT,
            party.endingReasonForHost(RealtimePartyEndTrigger.HOST_LEFT, liveStart.plusMinutes(5)),
        )
    }

    @Test
    fun `RealtimeParty - 정확히 10분 시점 종료는 경로와 무관하게 TIME_LIMIT_REACHED`() {
        val party = realtimeParty().apply { liveStartedAt = liveStart }

        RealtimePartyEndTrigger.entries.forEach { trigger ->
            assertEquals(
                RealtimePartyEndingReason.TIME_LIMIT_REACHED,
                party.endingReasonForHost(trigger, party.automaticEndingStartedAt()),
            )
        }
    }

    @Test
    fun `RealtimeParty - 예약 시각 전이면 주최자 나가기는 DELETE`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyHostExitAction.DELETE, party.hostExitAction(liveStart.minusNanos(1)))
    }

    @Test
    fun `RealtimeParty - 예약 시각 이후 미시작이면 주최자 나가기는 END`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyHostExitAction.END, party.hostExitAction(liveStart))
    }

    @Test
    fun `RealtimeParty - 종료 카운트다운 중이면 주최자 나가기 액션이 없다`() {
        val party = realtimeParty().apply { liveEndingStartedAt = liveStart.plusMinutes(1) }

        assertEquals(null, party.hostExitAction(liveStart.plusMinutes(1)))
    }

    @Test
    fun `RealtimeParty - 종료 카운트다운 중에는 종료 인사하기를 사용할 수 없다`() {
        val endingStartedAt = liveStart.plusMinutes(5)
        val party =
            realtimeParty().apply {
                liveStartedAt = liveStart
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

    @Test
    fun `RealtimeParty - 시작 시각이 있으면 시작 10분 후 종료가 시작된다`() {
        val liveStartedAt = liveStart.plusMinutes(3)
        val party = realtimeParty().apply { this.liveStartedAt = liveStartedAt }

        assertEquals(liveStartedAt.plusMinutes(10), party.automaticEndingStartedAt())
    }

    @Test
    fun `RealtimeParty - 시작 시각이 없으면 startedAt 30분 후가 마감선이다`() {
        val party = realtimeParty()

        assertEquals(liveStart.plusMinutes(30), party.automaticEndingStartedAt())
        assertEquals(liveStart.plusMinutes(30), party.startDeadlineAt())
    }

    @Test
    fun `RealtimeParty - 미시작 대기 구간은 LIVE_OPEN이다`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyStatus.LIVE_OPEN, party.status(liveStart.plusMinutes(29)))
    }

    @Test
    fun `RealtimeParty - 마감선에 도달하면 LIVE_ENDING이다`() {
        val party = realtimeParty()

        assertEquals(RealtimePartyStatus.LIVE_ENDING, party.status(liveStart.plusMinutes(30)))
    }

    @Test
    fun `RealtimeParty - 미시작이면 작별인사 시각이 없다`() {
        val party = realtimeParty()

        assertEquals(null, party.hostFarewellAvailableAt)
    }
}
