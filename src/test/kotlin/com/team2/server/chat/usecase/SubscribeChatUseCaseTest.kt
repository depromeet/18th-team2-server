package com.team2.server.chat.usecase

import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PaperOnlyParty
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
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class SubscribeChatUseCaseTest {

    @Mock lateinit var partyRepository: PartyRepository
    @Mock lateinit var participantRepository: ParticipantRepository
    @Mock lateinit var profileRepository: RealtimeParticipantProfileRepository
    @Mock lateinit var chatMessageRepository: ChatMessageRepository
    @Mock lateinit var sseEmitterRegistry: SseEmitterRegistry

    @InjectMocks
    lateinit var useCase: SubscribeChatUseCase

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "tok")
        }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "tok")
        }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `JWT + 파티 미소속이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = 99L, participantToken = null)
        }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = null)
        }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `구독 성공 - 히스토리 전송 후 emitter 등록`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val msg = ChatMessage(content = "기존메시지", party = party, profile = profile)

        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(profile)
        whenever(chatMessageRepository.findAllByPartyIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(msg))

        val emitter = useCase.subscribe(partyId = 1L, userId = 10L, participantToken = null)

        assertNotNull(emitter)
        verify(sseEmitterRegistry).subscribe(any(), any())
    }

    @Test
    fun `participantToken이 다른 파티 소속이면 PARTY_FORBIDDEN`() {
        val targetParty = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val otherParty = RealtimeParty(ownerId = 2L, startedAt = LocalDateTime.now().minusMinutes(5))
        val otherParticipant = Participant(party = otherParty)
        val profile = RealtimeParticipantProfile(participant = otherParticipant, nickname = "침입자")

        whenever(partyRepository.findPartyById(1L)).thenReturn(targetParty)
        whenever(profileRepository.findByParticipantToken("foreign-tok")).thenReturn(profile)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "foreign-tok")
        }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `participantToken으로 프로필 없으면 CHARACTER_REQUIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("unknown-tok")).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "unknown-tok")
        }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }
}
