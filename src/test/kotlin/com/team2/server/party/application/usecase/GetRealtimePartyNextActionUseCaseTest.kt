package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyCallerAccessService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class GetRealtimePartyNextActionUseCaseTest {
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase = mock()
    private val partyCallerAccessService: PartyCallerAccessService = mock()
    private val participantService: ParticipantService = mock()
    private val partyInviteService: PartyInviteService = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        GetRealtimePartyNextActionUseCase(
            resolveRealtimePartyUseCase,
            partyCallerAccessService,
            participantService,
            partyInviteService,
            clock,
        )

    @Test
    fun `LIVE_OPEN party throws REALTIME_PARTY_END_NOT_AVAILABLE after access validation`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(1))
        whenever(resolveRealtimePartyUseCase.invoke(1L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                useCase(1L, userId = 1L, participantToken = null, inviteToken = null)
            }

        verify(partyCallerAccessService).validateCallerCanAccessParty(1L, 1L, null)
        assertEquals(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE, ex.errorCode)
    }

    @Test
    fun `host gets host rolling paper list action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        whenever(resolveRealtimePartyUseCase.invoke(1L)).thenReturn(party)

        val result = useCase(1L, userId = 1L, participantToken = null, inviteToken = null)

        assertEquals(RealtimePartyNextActionResult.Host(1L), result)
    }

    @Test
    fun `participant gets rolling paper write action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        val participant = Participant(party = party).apply { hasWrittenPaper = true }
        whenever(resolveRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, null, "tok")).thenReturn(participant)
        whenever(partyInviteService.findNextActionInviteToken(eq(1L), any(), eq(null))).thenReturn("invite-token")

        val result = useCase(1L, userId = null, participantToken = "tok", inviteToken = null)

        assertEquals(RealtimePartyNextActionResult.Participant("invite-token", rollingPaperWritten = true), result)
    }

    @Test
    fun `participant request invite token is preferred for next action`() {
        val party = realtimeParty(id = 1L, ownerId = 1L, startedAt = now.minusMinutes(12))
        val participant = Participant(party = party)
        whenever(resolveRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, null, "tok")).thenReturn(participant)
        whenever(partyInviteService.findNextActionInviteToken(eq(1L), any(), eq("request-invite")))
            .thenReturn("request-invite")

        val result = useCase(1L, userId = null, participantToken = "tok", inviteToken = "request-invite")

        assertEquals(RealtimePartyNextActionResult.Participant("request-invite", rollingPaperWritten = false), result)
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
            runCatching {
                type.getDeclaredField("id").also { field ->
                    field.isAccessible = true
                    field.set(party, id)
                }
            }.onSuccess { return }
            type = type.superclass
        }
    }
}
