package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RealtimePartyEndServiceTest {
    private val partyRepository: PartyRepository = mock()
    private val endingInfoPort: RealtimePartyEndingInfoPort = mock()
    private val service = RealtimePartyEndService(partyRepository, endingInfoPort)
    private val startedAt = LocalDateTime.of(2026, 5, 23, 10, 0)

    @Test
    fun `startIfNotStarted returns affected count and realtime party`() {
        val party = realtimeParty(id = 1L, liveEndingStartedAt = startedAt.plusMinutes(5))
        whenever(partyRepository.startRealtimeEndingIfNotStarted(eq(1L), any())).thenReturn(1)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(endingInfoPort.get(party))
            .thenReturn(RealtimePartyEndingInfo(party.endingReason(), "주최자"))

        val result = service.startIfNotStarted(1L, startedAt.plusMinutes(5))

        assertEquals(1, result.affected)
        assertSame(party, result.party)
    }

    @Test
    fun `startIfNotStartedOrNull returns null when ending time is still absent`() {
        val party = realtimeParty(id = 1L)
        whenever(partyRepository.startRealtimeEndingIfNotStarted(eq(1L), any())).thenReturn(0)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val result = service.startIfNotStartedOrNull(1L, startedAt.plusMinutes(5))

        assertNull(result)
    }

    @Test
    fun `startIfNotStartedOrNull returns ending schedule`() {
        val endingStartedAt = startedAt.plusMinutes(5)
        val party = realtimeParty(id = 1L, liveEndingStartedAt = endingStartedAt)
        whenever(partyRepository.startRealtimeEndingIfNotStarted(eq(1L), any())).thenReturn(1)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(endingInfoPort.get(party))
            .thenReturn(RealtimePartyEndingInfo(party.endingReason(), "주최자"))

        val result = service.startIfNotStartedOrNull(1L, endingStartedAt)

        assertEquals(1L, result?.partyId)
        assertEquals(endingStartedAt, result?.endingStartedAt)
        assertEquals(endingStartedAt.plusSeconds(RealtimeParty.LIVE_END_COUNTDOWN_SECONDS), result?.endedAt)
        assertEquals(RealtimePartyEndingReason.HOST_REQUEST, result?.endingReason)
        assertEquals("주최자", result?.hostNickname)
        assertEquals(true, result?.startedNow)
    }

    @Test
    fun `startDueAutomaticEndings delegates policy constants`() {
        val now = startedAt.plusMinutes(10)

        service.startDueAutomaticEndings(now)

        verify(partyRepository).startAutomaticRealtimeEndings(
            now = now,
            liveDurationMinutes = RealtimeParty.LIVE_DURATION_MINUTES,
            partyEndedAfterDays = Party.ENDED_AFTER_DAYS,
        )
    }

    @Test
    fun `findRecoverySchedules maps automatic schedules from waiting party query`() {
        val party = realtimeParty(id = 2L)
        whenever(partyRepository.findRealtimePartiesWaitingAutomaticEnding(any())).thenReturn(listOf(party))

        val result = service.findRecoverySchedules(startedAt.plusMinutes(1))

        assertEquals(2L, result.automaticEndSchedules.single().partyId)
        assertEquals(party.automaticEndingStartedAt(), result.automaticEndSchedules.single().endingStartedAt)
        verify(partyRepository).findRealtimePartiesWaitingAutomaticEnding(any())
    }

    @Test
    fun `findEndingTargets maps started endings`() {
        val endingStartedAt = startedAt.plusMinutes(4)
        val party = realtimeParty(id = 3L, liveEndingStartedAt = endingStartedAt)
        whenever(partyRepository.findRealtimePartiesWithEndingStarted(any())).thenReturn(listOf(party))
        whenever(endingInfoPort.get(party))
            .thenReturn(RealtimePartyEndingInfo(party.endingReason(), "주최자"))

        val result = service.findEndingTargets(startedAt.plusDays(1))

        assertEquals(3L, result.single().partyId)
        assertEquals(endingStartedAt, result.single().endingStartedAt)
        assertEquals(endingStartedAt.plusSeconds(RealtimeParty.LIVE_END_COUNTDOWN_SECONDS), result.single().endedAt)
        assertEquals(RealtimePartyEndingReason.HOST_REQUEST, result.single().endingReason)
        assertEquals("주최자", result.single().hostNickname)
        assertEquals(false, result.single().startedNow)
    }

    @Test
    fun `missing party throws PARTY_NOT_FOUND`() {
        whenever(partyRepository.startRealtimeEndingIfNotStarted(eq(1L), any())).thenReturn(0)
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { service.startIfNotStarted(1L, startedAt) }

        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `non realtime party throws PARTY_NOT_REALTIME`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = startedAt)
        whenever(partyRepository.startRealtimeEndingIfNotStarted(eq(1L), any())).thenReturn(0)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { service.startIfNotStarted(1L, startedAt) }

        assertEquals(ErrorCode.PARTY_NOT_REALTIME, ex.errorCode)
    }

    private fun realtimeParty(
        id: Long,
        liveEndingStartedAt: LocalDateTime? = null,
    ): RealtimeParty =
        RealtimeParty(ownerId = 1L, startedAt = startedAt, liveEndingStartedAt = liveEndingStartedAt)
            .also { setId(it, id) }

    private fun setId(
        party: Party,
        id: Long,
    ) {
        var type: Class<*>? = party.javaClass
        while (type != null) {
            try {
                type.getDeclaredField("id").also { field ->
                    field.isAccessible = true
                    field.set(party, id)
                }
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            } catch (ex: ReflectiveOperationException) {
                throw IllegalStateException("Failed to set id=$id on party=${party.javaClass.name}", ex)
            }
        }
        throw IllegalStateException("Could not find id field to set id=$id on party=${party.javaClass.name}")
    }
}
