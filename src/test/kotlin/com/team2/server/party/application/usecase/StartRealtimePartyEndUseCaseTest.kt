package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.port.BurstGameCompletionReader
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class StartRealtimePartyEndUseCaseTest {
    private val partyService: PartyService = mock()
    private val realtimePartyEndService: RealtimePartyEndService = mock()
    private val burstGameCompletionReader: BurstGameCompletionReader = mock()
    private val eventPublisher: RealtimePartyEndingEventPublisher = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        StartRealtimePartyEndUseCase(
            partyService,
            realtimePartyEndService,
            burstGameCompletionReader,
            eventPublisher,
            clock,
        )

    @Test
    fun `non host cannot start realtime party ending`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(5))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase(1L, userId = 2L) }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `LIVE_CLOSED party cannot start ending again`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase(1L, userId = 1L) }

        assertEquals(ErrorCode.REALTIME_PARTY_ALREADY_ENDED, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN before host available time cannot start ending`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(1))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase(1L, userId = 1L) }

        assertEquals(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN host starts ending and publishes event when newly persisted`() {
        val endingStartedAt = now
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(5))
        val endedParty = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(5), endingStartedAt)
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(realtimePartyEndService.startIfNotStarted(1L, endingStartedAt))
            .thenReturn(RealtimePartyEndStartResult(affected = 1, party = endedParty))

        val result = useCase(1L, userId = 1L)

        assertEquals(endingStartedAt, result.endingStartedAt)
        verify(eventPublisher).publish(result)
    }

    @Test
    fun `LIVE_OPEN host can start ending before four minutes when burst game ended`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(1))
        val endedParty =
            realtimeParty(
                id = 1L,
                ownerId = 1L,
                startedAt = now.minusMinutes(1),
                liveEndingStartedAt = now,
            )
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(burstGameCompletionReader.isCompleted(1L, now)).thenReturn(true)
        whenever(realtimePartyEndService.startIfNotStarted(1L, now))
            .thenReturn(RealtimePartyEndStartResult(affected = 1, party = endedParty))

        val result = useCase(1L, userId = 1L)

        assertEquals(now, result.endingStartedAt)
    }

    @Test
    fun `LIVE_ENDING with existing ending returns result without publishing duplicate event`() {
        val endingStartedAt = now.minusSeconds(10)
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(10), endingStartedAt)
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val result = useCase(1L, userId = 1L)

        assertEquals(endingStartedAt, result.endingStartedAt)
        verify(realtimePartyEndService, never()).startIfNotStarted(any(), any())
        verifyNoInteractions(eventPublisher)
    }

    @Test
    fun `automatic LIVE_ENDING without persisted ending is persisted and notifies only when affected`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(10).minusSeconds(10))
        val endedParty =
            realtimeParty(
                id = 1L,
                ownerId = 1L,
                startedAt = now.minusMinutes(10).minusSeconds(10),
                liveEndingStartedAt = party.automaticEndingStartedAt(),
            )
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(realtimePartyEndService.startIfNotStarted(1L, party.automaticEndingStartedAt()))
            .thenReturn(RealtimePartyEndStartResult(affected = 0, party = endedParty))

        val result = useCase(1L, userId = 1L)

        assertEquals(party.automaticEndingStartedAt(), result.endingStartedAt)
        verifyNoInteractions(eventPublisher)
    }

    private fun realtimeParty(
        id: Long,
        ownerId: Long,
        startedAt: LocalDateTime,
        liveEndingStartedAt: LocalDateTime? = null,
    ): RealtimeParty =
        RealtimeParty(ownerId = ownerId, startedAt = startedAt, liveEndingStartedAt = liveEndingStartedAt)
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
