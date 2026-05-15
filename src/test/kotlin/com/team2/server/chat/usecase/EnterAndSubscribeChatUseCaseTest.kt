package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.chat.infrastructure.sse.PartyEndScheduler
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterAndSubscribeChatUseCaseTest {
    @Mock lateinit var enterRealtimePartyUseCase: EnterRealtimePartyUseCase

    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var imageUrlReader: ImageUrlReader

    @Mock lateinit var chatSseGateway: ChatSseGateway

    @Mock lateinit var partyEndScheduler: PartyEndScheduler

    @InjectMocks
    lateinit var useCase: EnterAndSubscribeChatUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    private fun enterResult(
        partyId: Long = 1L,
        isCelebrant: Boolean = false,
    ) = EnterRealtimePartyUseCase.EnterResult(
        participantToken = "abc12345",
        partyId = partyId,
        startedAt = LocalDateTime.now().minusMinutes(5),
        isCelebrant = isCelebrant,
        nickname = "토끼왕",
        characterId = 1L,
    )

    @Test
    fun `입장 성공 - entered 이벤트와 함께 emitter 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val msg = ChatMessage(content = "이전 메시지", party = party, profile = profile)

        whenever(enterRealtimePartyUseCase.enter("tok", null, request))
            .thenReturn(enterResult())
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(listOf(msg))
        whenever(imageUrlReader.findFirstImageUrlByTargetIds(any(), any())).thenReturn(emptyMap())

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
        verify(partyEndScheduler).scheduleIfNeeded(eq(1L), any())
    }

    @Test
    fun `히스토리 없어도 entered 이벤트 전송`() {
        whenever(enterRealtimePartyUseCase.enter("tok", null, request))
            .thenReturn(enterResult())
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(emptyList())
        whenever(imageUrlReader.findFirstImageUrlByTargetIds(any(), any())).thenReturn(emptyMap())

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
        verify(partyEndScheduler).scheduleIfNeeded(eq(1L), any())
    }

    @Test
    fun `입장 성공 - user-entered 이벤트 브로드캐스트`() {
        whenever(enterRealtimePartyUseCase.enter("tok", null, request))
            .thenReturn(enterResult(isCelebrant = true))
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(emptyList())
        whenever(imageUrlReader.findFirstImageUrlByTargetIds(any(), any()))
            .thenReturn(mapOf(1L to "https://example.com/rabbit.png"))

        useCase.enterAndSubscribe("tok", null, request)

        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(partyEndScheduler).scheduleIfNeeded(eq(1L), any())
    }
}
