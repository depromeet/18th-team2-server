package com.team2.server.chat.usecase

import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
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
class SendChatMessageUseCaseTest {
    @Mock lateinit var partyRepository: PartyRepository

    @Mock lateinit var participantRepository: ParticipantRepository

    @Mock lateinit var profileRepository: RealtimeParticipantProfileRepository

    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var sseEmitterRegistry: SseEmitterRegistry

    @InjectMocks
    lateinit var useCase: SendChatMessageUseCase

    private val request = SendChatMessageRequest(content = "안녕하세요!")

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN이 아니면 CHAT_NOT_ACTIVE`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `JWT + 파티 미소속이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 99L)).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = 99L, participantToken = null, request)
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `JWT + 프로필 없으면 CHARACTER_REQUIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)
            }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `participantToken + 다른 파티 프로필이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val otherParty = RealtimeParty(ownerId = 2L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = otherParty)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "닉", participantToken = "tok")
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("tok")).thenReturn(profile)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `JWT로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)

        assertEquals("안녕하세요!", response.content)
        assertEquals("토끼왕", response.senderNickname)
        verify(sseEmitterRegistry).broadcast(any(), any())
    }

    @Test
    fun `participantToken으로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님", participantToken = "tok")
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("tok")).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)

        assertEquals("손님", response.senderNickname)
        verify(sseEmitterRegistry).broadcast(any(), any())
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                useCase.send(partyId = 1L, userId = null, participantToken = null, request)
            }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `participantToken + 프로필 없으면 CHARACTER_REQUIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("tok")).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
        }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }
}
