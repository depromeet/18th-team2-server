package com.team2.server.chat.usecase

import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyUseCase
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class LeaveChatUseCaseTest {
    @Mock lateinit var resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase

    @Mock lateinit var resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase

    @Mock lateinit var chatSseGateway: ChatSseGateway

    @InjectMocks
    lateinit var useCase: LeaveChatUseCase

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(resolveRealtimePartyUseCase.invoke(1L))
            .thenThrow(BusinessException(ErrorCode.PARTY_NOT_FOUND))

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        whenever(resolveRealtimePartyUseCase.invoke(1L))
            .thenThrow(BusinessException(ErrorCode.CHAT_NOT_SUPPORTED))

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `profileService가 CHARACTER_REQUIRED 던지면 전파됨`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(resolveRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(1L, null, "tok"))
            .thenThrow(BusinessException(ErrorCode.CHARACTER_REQUIRED))

        val ex = assertThrows<BusinessException> { useCase.leave(1L, null, "tok") }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `퇴장 성공 - user-left 이벤트 브로드캐스트`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = true)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "주인공", participantToken = "tok")
        whenever(resolveRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, null, "tok")).thenReturn(profile)

        useCase.leave(party.id, null, "tok")

        verify(chatSseGateway).leave("tok")
        verify(chatSseGateway).broadcastAfterCommit(any(), any(), anyOrNull())
    }

    @Test
    fun `userId로 퇴장 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "참가자")
        whenever(resolveRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, 10L, null)).thenReturn(profile)

        useCase.leave(party.id, 10L, null)

        verify(chatSseGateway).broadcastAfterCommit(any(), any(), anyOrNull())
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(resolveRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, null, null))
            .thenThrow(BusinessException(ErrorCode.UNAUTHORIZED))

        val ex = assertThrows<BusinessException> { useCase.leave(party.id, null, null) }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }
}
