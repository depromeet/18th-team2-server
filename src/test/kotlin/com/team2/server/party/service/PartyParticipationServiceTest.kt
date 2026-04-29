package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.lang.reflect.Field
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class PartyParticipationServiceTest {
    @Mock
    lateinit var partyInviteRepository: PartyInviteRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var characterRepository: CharacterRepository

    @Mock
    lateinit var characterImageUrlResolver: CharacterImageUrlResolver

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var partyParticipationService: PartyParticipationService

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val idField: Field = entity.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity, id)
    }

    private fun newParty(
        id: Long = 1L,
        endedAt: LocalDateTime = LocalDateTime.now().plusDays(7),
        isChattingAllow: Boolean = true,
    ): Party {
        val party =
            Party(
                ownerId = 1L,
                name = "생일파티",
                celebrantNickname = "홍길동",
                purpose = PartyPurpose.BIRTHDAY,
                option = PartyOption.REALTIME,
                startedAt = LocalDateTime.now(),
                endedAt = endedAt,
                isChattingAllow = isChattingAllow,
            )
        setId(party, id)
        return party
    }

    private fun newInvite(
        token: String = "abc123",
        party: Party = newParty(),
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),
    ) = PartyInvite(
        party = party,
        token = token,
        expiresAt = expiresAt,
    )

    private fun newUser(id: Long = 10L): User {
        val user =
            User(
                name = "유저",
                birthDay = "01-01",
                provider = AuthProvider.KAKAO,
                providerId = "kakao-1",
                email = "u@kakao.local",
            )
        setId(user, id)
        return user
    }

    private fun newCharacter(id: Long = 1L): Character {
        val character = Character(name = "곰돌이")
        setId(character, id)
        return character
    }

    @Test
    fun `joinParty 비회원 참여 성공`() {
        val party = newParty()
        val character = newCharacter()

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(characterImageUrlResolver.resolve(character)).thenReturn("/images/characters/character1.jpg")
        whenever(participantRepository.saveAndFlush(any<Participant>())).thenAnswer {
            val p = it.getArgument<Participant>(0)
            setId(p, 99L)
            p
        }

        val result = partyParticipationService.joinParty("abc123", null, "참여자", 1L)

        assertEquals(99L, result.participantId)
        assertEquals("참여자", result.nickname)
        assertEquals("/images/characters/character1.jpg", result.characterImageUrl)
    }

    @Test
    fun `joinParty 회원 참여 성공`() {
        val party = newParty()
        val user = newUser()
        val character = newCharacter()

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.existsByPartyAndUser(party, user)).thenReturn(false)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(characterImageUrlResolver.resolve(character)).thenReturn("/images/characters/character1.jpg")
        whenever(participantRepository.saveAndFlush(any<Participant>())).thenAnswer {
            val p = it.getArgument<Participant>(0)
            setId(p, 100L)
            p
        }

        val result = partyParticipationService.joinParty("abc123", 10L, "회원참여자", 1L)

        assertEquals(100L, result.participantId)
        assertEquals("회원참여자", result.nickname)
        assertEquals("/images/characters/character1.jpg", result.characterImageUrl)
    }

    @Test
    fun `joinParty 존재하지 않는 shareLink면 PARTY_NOT_FOUND`() {
        whenever(partyInviteRepository.findByToken("no-link")).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("no-link", null, "닉", 1L)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `joinParty 종료된 파티면 PARTY_ENDED`() {
        val party = newParty(endedAt = LocalDateTime.now().minusDays(1))
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", null, "닉", 1L)
            }
        assertEquals(ErrorCode.PARTY_ENDED, ex.errorCode)
    }

    @Test
    fun `joinParty 종료 시간이 현재와 같으면 PARTY_ENDED`() {
        val party = newParty(endedAt = LocalDateTime.now())
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", null, "닉", 1L)
            }
        assertEquals(ErrorCode.PARTY_ENDED, ex.errorCode)
    }

    @Test
    fun `joinParty 만료된 초대 토큰이면 INVITE_LINK_EXPIRED`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("expired")).thenReturn(
            newInvite(token = "expired", party = party, expiresAt = LocalDateTime.now().minusSeconds(1)),
        )

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("expired", null, "닉", 1L)
            }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `joinParty 회원 중복 참여면 ALREADY_JOINED`() {
        val party = newParty()
        val user = newUser()

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.existsByPartyAndUser(party, user)).thenReturn(true)

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", 10L, "닉", 1L)
            }
        assertEquals(ErrorCode.ALREADY_JOINED, ex.errorCode)
    }

    @Test
    fun `joinParty DB unique 제약으로 중복 참여가 감지되면 ALREADY_JOINED`() {
        val party = newParty()
        val user = newUser()
        val character = newCharacter()

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.existsByPartyAndUser(party, user)).thenReturn(false)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.saveAndFlush(any<Participant>())).thenThrow(
            DataIntegrityViolationException("duplicate key uk_participant_party_user"),
        )

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", 10L, "닉", 1L)
            }

        assertEquals(ErrorCode.ALREADY_JOINED, ex.errorCode)
    }

    @Test
    fun `joinParty 회원 userId가 DB에 없으면 AUTH_USER_NOT_FOUND`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", 10L, "닉", 1L)
            }

        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `joinParty 존재하지 않는 characterId면 CHARACTER_NOT_FOUND`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(characterRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", null, "닉", 999L)
            }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `joinParty 채팅 허용 파티에서 characterId 없으면 CHARACTER_REQUIRED`() {
        val party = newParty(isChattingAllow = true)
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", null, "닉", null)
            }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `joinParty 채팅 비허용 파티에서 characterId 있으면 CHARACTER_NOT_ALLOWED`() {
        val party = newParty(isChattingAllow = false)
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val ex =
            assertThrows<BusinessException> {
                partyParticipationService.joinParty("abc123", null, "닉", 1L)
            }
        assertEquals(ErrorCode.CHARACTER_NOT_ALLOWED, ex.errorCode)
    }

    @Test
    fun `joinParty 채팅 비허용 파티는 characterId 없이 참여 성공`() {
        val party = newParty(isChattingAllow = false)

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(participantRepository.saveAndFlush(any<Participant>())).thenAnswer {
            val p = it.getArgument<Participant>(0)
            setId(p, 101L)
            p
        }

        val result = partyParticipationService.joinParty("abc123", null, "참여자", null)

        assertEquals(101L, result.participantId)
        assertEquals("참여자", result.nickname)
        assertNull(result.characterImageUrl)
    }
}
