package com.team2.server.chat.usecase

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.domain.entity.Character
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
class SendChatMessageUseCaseTest {
    @Mock lateinit var resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase

    @Mock lateinit var resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase

    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var imageUrlReader: ImageUrlReader

    @Mock lateinit var chatSseGateway: ChatSseGateway

    @InjectMocks
    lateinit var useCase: SendChatMessageUseCase

    private val request = SendChatMessageRequest(content = "안녕하세요!")

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L))
            .thenThrow(BusinessException(ErrorCode.PARTY_NOT_FOUND))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L))
            .thenThrow(BusinessException(ErrorCode.CHAT_NOT_SUPPORTED))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN이 아니면 CHAT_NOT_ACTIVE`() {
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L))
            .thenThrow(BusinessException(ErrorCode.CHAT_NOT_ACTIVE))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `profileService가 PARTY_FORBIDDEN 던지면 전파됨`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(1L, 99L, null))
            .thenThrow(BusinessException(ErrorCode.PARTY_FORBIDDEN))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = 99L, participantToken = null, request)
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `profileService가 CHARACTER_REQUIRED 던지면 전파됨`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(1L, 10L, null))
            .thenThrow(BusinessException(ErrorCode.CHARACTER_REQUIRED))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)
            }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `JWT로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = true)
        val character = Character(name = "토끼")
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(1L, 10L, null)).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)
        whenever(imageUrlReader.findFirstImageUrlByTargetIds(ImageTargetType.CHARACTER, listOf(character.id)))
            .thenReturn(mapOf(character.id to "https://example.com/rabbit.png"))

        val response = useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)

        assertEquals("안녕하세요!", response.content)
        assertEquals("토끼왕", response.senderNickname)
        assertEquals(ParticipantRole.CELEBRANT, response.senderRole)
        assertEquals("https://example.com/rabbit.png", response.senderCharacterImageUrl)
        verify(chatSseGateway).broadcastAfterCommit(any(), any(), anyOrNull())
    }

    @Test
    fun `participantToken으로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님", participantToken = "tok")
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, null, "tok")).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = useCase.send(partyId = party.id, userId = null, participantToken = "tok", request)

        assertEquals("손님", response.senderNickname)
        assertEquals(ParticipantRole.PARTICIPANT, response.senderRole)
        assertEquals(null, response.senderCharacterImageUrl)
        verify(chatSseGateway).broadcastAfterCommit(any(), any(), anyOrNull())
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(1L)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(1L, null, null))
            .thenThrow(BusinessException(ErrorCode.UNAUTHORIZED))

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = null, request)
            }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }
}
