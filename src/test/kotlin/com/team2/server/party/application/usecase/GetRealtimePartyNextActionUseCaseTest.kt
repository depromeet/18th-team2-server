package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class GetRealtimePartyNextActionUseCaseTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val partyInviteService: PartyInviteService = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        GetRealtimePartyNextActionUseCase(
            partyService,
            participantService,
            partyInviteService,
            clock,
        )

    @Test
    fun `LIVE_OPEN party throws REALTIME_PARTY_INVALID_STATE`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(1))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                useCase(1L, userId = 1L, participantToken = null)
            }

        assertEquals(ErrorCode.REALTIME_PARTY_INVALID_STATE, ex.errorCode)
    }

    @Test
    fun `invalid participant token throws PARTY_FORBIDDEN after state validation`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, null, "bad-token"))
            .thenThrow(BusinessException(ErrorCode.PARTY_FORBIDDEN))

        val ex =
            assertThrows<BusinessException> {
                useCase(1L, userId = null, participantToken = "bad-token")
            }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `host gets host rolling paper list action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val result = useCase(1L, userId = 1L, participantToken = null)

        assertEquals(RealtimePartyNextActionResult.Host(1L), result)
    }

    @Test
    fun `participant gets rolling paper write action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        val participant = Participant(party = party).apply { hasWrittenPaper = true }
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, null, "tok")).thenReturn(participant)
        whenever(partyInviteService.findLatestUsableInviteToken(eq(1L), any())).thenReturn("invite-token")

        val result = useCase(1L, userId = null, participantToken = "tok")

        assertEquals(RealtimePartyNextActionResult.Participant("invite-token", rollingPaperWritten = true), result)
    }

    @Test
    fun `host participant token gets host rolling paper list action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        val hostParticipant = Participant(party = party, isCelebrant = true)
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, null, "host-token")).thenReturn(hostParticipant)

        val result = useCase(1L, userId = null, participantToken = "host-token")

        assertEquals(RealtimePartyNextActionResult.Host(1L), result)
    }

    private fun realtimeParty(
        id: Long,
        ownerId: Long,
        startedAt: LocalDateTime,
    ): RealtimeParty =
        RealtimeParty(ownerId = ownerId, startedAt = startedAt)
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
