package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterRealtimePartyUseCaseTest {
    @Mock lateinit var partyInviteRepository: PartyInviteRepository

    @Mock lateinit var participantRepository: ParticipantRepository

    @Mock lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository

    @Mock lateinit var characterRepository: CharacterRepository

    @Mock lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var useCase: EnterRealtimePartyUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    @Test
    fun `존재하지 않는 초대 토큰이면 PARTY_NOT_FOUND`() {
        whenever(partyInviteRepository.findByToken("invalid")).thenReturn(null)

        val ex = assertThrows<BusinessException> { useCase.enter("invalid", userId = null, request) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `만료된 초대링크면 INVITE_LINK_EXPIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().minusSeconds(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 캐릭터면 CHARACTER_NOT_FOUND`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `비로그인 사용자 첫 입장 - 익명 Participant + Profile 생성 후 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)

        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(any())).thenReturn(participant)
        whenever(realtimeParticipantProfileRepository.findByParticipant(participant)).thenReturn(null)
        whenever(realtimeParticipantProfileRepository.save(any())).thenReturn(profile)

        val response = useCase.enter("tok", userId = null, request)

        assertNotNull(response.participantToken)
    }

    @Test
    fun `이미 프로필이 있는 사용자 재입장 - 기존 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val existingProfile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-uuid",
            )

        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(any())).thenReturn(participant)
        whenever(realtimeParticipantProfileRepository.findByParticipant(participant)).thenReturn(existingProfile)

        val response = useCase.enter("tok", userId = null, request)

        assertEquals("existing-uuid", response.participantToken)
    }
}
