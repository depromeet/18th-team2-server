package com.team2.server.chat.application.support

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.usecase.StartRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ChatLeaveExecutorTest {
    @Mock lateinit var participantService: ParticipantService

    @Mock lateinit var startRealtimePartyEndUseCase: StartRealtimePartyEndUseCase

    @InjectMocks
    lateinit var executor: ChatLeaveExecutor

    @Test
    fun `참가자를 퇴장시키고 퇴장 페이로드를 만든다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = true)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "주인공", participantToken = "tok")

        val payload = executor.execute(party, profile, null)

        verify(participantService).leave(participant)
        verify(startRealtimePartyEndUseCase, never()).invoke(any(), any())
        assertEquals("주인공", payload.nickname)
        assertEquals(ParticipantRole.CELEBRANT, payload.role)
    }

    @Test
    fun `셀러브런트가 아니면 PARTICIPANT 역할로 내려간다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님")

        val payload = executor.execute(party, profile, null)

        assertEquals(ParticipantRole.PARTICIPANT, payload.role)
    }

    @Test
    fun `주최자가 퇴장하면 파티 종료를 시작한다`() {
        val party = RealtimeParty(ownerId = 10L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "주최자", participantToken = "tok")

        executor.execute(party, profile, 10L)

        verify(participantService).leave(participant)
        verify(startRealtimePartyEndUseCase).invoke(party.id, party.ownerId)
    }
}
