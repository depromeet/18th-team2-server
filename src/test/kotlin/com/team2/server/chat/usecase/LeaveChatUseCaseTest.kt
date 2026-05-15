package com.team2.server.chat.usecase

import com.team2.server.chat.service.ChatSseService
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class LeaveChatUseCaseTest {
    @Mock lateinit var partyRepository: PartyRepository

    @Mock lateinit var profileService: RealtimeParticipantProfileService

    @Mock lateinit var chatSseService: ChatSseService

    @InjectMocks
    lateinit var useCase: LeaveChatUseCase

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `profileService가 CHARACTER_REQUIRED 던지면 전파됨`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileService.resolveProfile(1L, null, "tok"))
            .thenThrow(BusinessException(ErrorCode.CHARACTER_REQUIRED))

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `퇴장 성공 - user-left 이벤트 브로드캐스트`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = true)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "주인공", participantToken = "tok")
        whenever(partyRepository.findPartyById(party.id)).thenReturn(party)
        whenever(profileService.resolveProfile(party.id, null, "tok")).thenReturn(profile)

        useCase.leave(party.id, null, "tok")

        verify(chatSseService).leave("tok")
        verify(chatSseService).broadcastAfterCommit(any(), any())
    }

    @Test
    fun `userId로 퇴장 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "참가자")
        whenever(partyRepository.findPartyById(party.id)).thenReturn(party)
        whenever(profileService.resolveProfile(party.id, 10L, null)).thenReturn(profile)

        useCase.leave(party.id, 10L, null)

        verify(chatSseService).broadcastAfterCommit(any(), any())
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(party.id)).thenReturn(party)
        whenever(profileService.resolveProfile(party.id, null, null))
            .thenThrow(BusinessException(ErrorCode.UNAUTHORIZED))

        val ex = assertThrows<BusinessException> { useCase.leave(party.id, null, null) }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }
}
