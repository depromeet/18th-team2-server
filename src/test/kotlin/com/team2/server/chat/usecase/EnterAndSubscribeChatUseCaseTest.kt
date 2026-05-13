package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterAndSubscribeChatUseCaseTest {
    @Mock lateinit var enterRealtimePartyUseCase: EnterRealtimePartyUseCase

    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var sseEmitterRegistry: SseEmitterRegistry

    @InjectMocks
    lateinit var useCase: EnterAndSubscribeChatUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    @Test
    fun `입장 성공 - entered 이벤트와 함께 emitter 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val msg = ChatMessage(content = "이전 메시지", party = party, profile = profile)

        whenever(enterRealtimePartyUseCase.enter("tok", null, request))
            .thenReturn(EnterRealtimePartyUseCase.EnterResult(participantToken = "abc12345", partyId = 1L))
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(listOf(msg))

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(sseEmitterRegistry).subscribe(any(), any())
    }

    @Test
    fun `히스토리 없어도 entered 이벤트 전송`() {
        whenever(enterRealtimePartyUseCase.enter("tok", null, request))
            .thenReturn(EnterRealtimePartyUseCase.EnterResult(participantToken = "abc12345", partyId = 1L))
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(emptyList())

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(sseEmitterRegistry).subscribe(any(), any())
    }
}
