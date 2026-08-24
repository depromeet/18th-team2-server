package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.application.service.RealtimePartyEndResultService
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    private val endingInfoPort: RealtimePartyEndingInfoPort = mock()
    private val eventPublisher: RealtimePartyEndingEventPublisher = mock()
    private val phaseStore: PartyPhaseStore = mock()
    private val endResultService = RealtimePartyEndResultService(endingInfoPort, eventPublisher, phaseStore)
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        StartRealtimePartyEndUseCase(
            partyService,
            realtimePartyEndService,
            endResultService,
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
        val startedAt = now.minusMinutes(12)
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = startedAt).apply { liveStartedAt = startedAt }
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase(1L, userId = 1L) }

        assertEquals(ErrorCode.REALTIME_PARTY_ALREADY_ENDED, ex.errorCode)
    }

    @Test
    fun `ROLLING_PAPER_OPEN party cannot start ending`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.plusMinutes(1))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase(1L, userId = 1L) }

        assertEquals(ErrorCode.REALTIME_PARTY_INVALID_STATE, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN host before farewell availability starts HOST_LEFT ending`() {
        val endingStartedAt = now
        val party =
            realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(1)).apply {
                liveStartedAt = now.minusMinutes(1)
            }
        val endedParty =
            realtimeParty(
                id = 1L,
                ownerId = 1L,
                startedAt = now.minusMinutes(1),
                liveEndingStartedAt = endingStartedAt,
                liveEndingReason = RealtimePartyEndingReason.HOST_LEFT,
            )
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(realtimePartyEndService.startIfNotStarted(1L, endingStartedAt, RealtimePartyEndingReason.HOST_LEFT))
            .thenReturn(RealtimePartyEndStartResult(affected = 1, party = endedParty))
        whenever(endingInfoPort.get(endedParty))
            .thenReturn(RealtimePartyEndingInfo(endedParty.endingReason(), "주최자"))

        val result = useCase(1L, userId = 1L)

        assertEquals(endingStartedAt, result.endingStartedAt)
        assertEquals(RealtimePartyEndingReason.HOST_LEFT, result.endingReason)
        assertEquals(now, result.serverNow)
        verify(phaseStore).forceSet(1L, PartyPhase.END, endingStartedAt)
        verify(eventPublisher).publish(result)
    }

    @Test
    fun `LIVE_ENDING with existing ending returns result without publishing duplicate event`() {
        val endingStartedAt = now.minusSeconds(10)
        val party =
            realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(10), endingStartedAt).apply {
                liveStartedAt = now.minusMinutes(5)
            }
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(
            realtimePartyEndService.startIfNotStarted(
                1L,
                endingStartedAt,
                RealtimePartyEndingReason.HOST_REQUEST,
            ),
        ).thenReturn(RealtimePartyEndStartResult(affected = 0, party = party))
        whenever(endingInfoPort.get(party))
            .thenReturn(RealtimePartyEndingInfo(party.endingReason(), "주최자"))

        val result = useCase(1L, userId = 1L)

        assertEquals(endingStartedAt, result.endingStartedAt)
        assertEquals(now, result.serverNow)
        verify(realtimePartyEndService)
            .startIfNotStarted(1L, endingStartedAt, RealtimePartyEndingReason.HOST_REQUEST)
        verify(phaseStore).forceSet(1L, PartyPhase.END, endingStartedAt)
        verifyNoInteractions(eventPublisher)
    }

    @Test
    fun `automatic LIVE_ENDING without persisted ending is persisted and notifies only when affected`() {
        val startedAt = now.minusMinutes(10).minusSeconds(10)
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = startedAt).apply { liveStartedAt = startedAt }
        val endedParty =
            realtimeParty(
                id = 1L,
                ownerId = 1L,
                startedAt = startedAt,
                liveEndingStartedAt = party.automaticEndingStartedAt(),
            ).apply { liveStartedAt = startedAt }
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(
            realtimePartyEndService.startIfNotStarted(
                1L,
                party.automaticEndingStartedAt(),
                RealtimePartyEndingReason.TIME_LIMIT_REACHED,
            ),
        ).thenReturn(RealtimePartyEndStartResult(affected = 0, party = endedParty))
        whenever(endingInfoPort.get(endedParty))
            .thenReturn(RealtimePartyEndingInfo(endedParty.endingReason(), "주최자"))

        val result = useCase(1L, userId = 1L)

        assertEquals(party.automaticEndingStartedAt(), result.endingStartedAt)
        verify(phaseStore).forceSet(eq(1L), eq(PartyPhase.END), eq(party.automaticEndingStartedAt()))
        verifyNoInteractions(eventPublisher)
    }

    private fun realtimeParty(
        id: Long,
        ownerId: Long,
        startedAt: LocalDateTime,
        liveEndingStartedAt: LocalDateTime? = null,
        liveEndingReason: RealtimePartyEndingReason? = null,
    ): RealtimeParty =
        RealtimeParty(
            ownerId = ownerId,
            startedAt = startedAt,
            liveEndingStartedAt = liveEndingStartedAt,
            liveEndingReason = liveEndingReason,
        ).also { setId(it, id) }

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
