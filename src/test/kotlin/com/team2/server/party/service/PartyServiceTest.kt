package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class PartyServiceTest {
    @Mock
    lateinit var partyRepository: PartyRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var characterRepository: CharacterRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var partyService: PartyService

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
        shareLink: String = "abc123",
        endedAt: LocalDateTime = LocalDateTime.now().plusDays(7),
    ): Party {
        val party =
            Party(
                shareLink = shareLink,
                name = "생일파티",
                celebrantNickname = "홍길동",
                purpose = PartyPurpose.BIRTHDAY,
                option = PartyOption.REALTIME,
                startedAt = LocalDateTime.now(),
                endedAt = endedAt,
                isChattingAllow = true,
            )
        setId(party, id)
        return party
    }

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

    // --- 파티 정보 조회 ---

    @Test
    fun `getPartyInfo 존재하는 파티 정보 반환`() {
        val party = newParty()
        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)

        val result = partyService.getPartyInfo("abc123", null)

        assertEquals("생일파티", result.name)
        assertEquals("홍길동", result.celebrantNickname)
        assertEquals(PartyPurpose.BIRTHDAY, result.purpose)
        assertEquals(PartyOption.REALTIME, result.option)
        assertTrue(result.isChattingAllow)
        assertFalse(result.ended)
        assertNull(result.myParticipant)
    }

    @Test
    fun `getPartyInfo 종료된 파티도 정상 반환하고 ended가 true`() {
        val party = newParty(endedAt = LocalDateTime.now().minusDays(1))
        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)

        val result = partyService.getPartyInfo("abc123", null)

        assertTrue(result.ended)
    }

    @Test
    fun `getPartyInfo 존재하지 않는 shareLink면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findByShareLink("no-link")).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyService.getPartyInfo("no-link", null)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `getPartyInfo 회원이 이미 참여했으면 myParticipant 포함`() {
        val party = newParty()
        val user = newUser()
        val character = newCharacter()
        val participant =
            Participant(
                party = party,
                character = character,
                user = user,
                nickname = "닉네임",
            )
        setId(participant, 5L)

        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.findByPartyAndUser(party, user)).thenReturn(participant)

        val result = partyService.getPartyInfo("abc123", 10L)

        assertNotNull(result.myParticipant)
        assertEquals(5L, result.myParticipant!!.participantId)
        assertEquals("닉네임", result.myParticipant!!.nickname)
        assertEquals(1L, result.myParticipant!!.characterId)
    }

    @Test
    fun `getPartyInfo 회원이 미참여면 myParticipant가 null`() {
        val party = newParty()
        val user = newUser()

        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.findByPartyAndUser(party, user)).thenReturn(null)

        val result = partyService.getPartyInfo("abc123", 10L)

        assertNull(result.myParticipant)
    }

    // --- 파티 참여 ---

    @Test
    fun `joinParty 비회원 참여 성공`() {
        val party = newParty()
        val character = newCharacter()

        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(org.mockito.kotlin.any<Participant>())).thenAnswer {
            val p = it.getArgument<Participant>(0)
            setId(p, 99L)
            p
        }

        val result = partyService.joinParty("abc123", null, "참여자", 1L)

        assertEquals(99L, result.participantId)
        assertEquals("참여자", result.nickname)
        assertEquals(1L, result.characterId)
    }

    @Test
    fun `joinParty 회원 참여 성공`() {
        val party = newParty()
        val user = newUser()
        val character = newCharacter()

        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.existsByPartyAndUser(party, user)).thenReturn(false)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(org.mockito.kotlin.any<Participant>())).thenAnswer {
            val p = it.getArgument<Participant>(0)
            setId(p, 100L)
            p
        }

        val result = partyService.joinParty("abc123", 10L, "회원참여자", 1L)

        assertEquals(100L, result.participantId)
        assertEquals("회원참여자", result.nickname)
    }

    @Test
    fun `joinParty 존재하지 않는 shareLink면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findByShareLink("no-link")).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyService.joinParty("no-link", null, "닉", 1L)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `joinParty 종료된 파티면 PARTY_ENDED`() {
        val party = newParty(endedAt = LocalDateTime.now().minusDays(1))
        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                partyService.joinParty("abc123", null, "닉", 1L)
            }
        assertEquals(ErrorCode.PARTY_ENDED, ex.errorCode)
    }

    @Test
    fun `joinParty 회원 중복 참여면 ALREADY_JOINED`() {
        val party = newParty()
        val user = newUser()

        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.existsByPartyAndUser(party, user)).thenReturn(true)

        val ex =
            assertThrows<BusinessException> {
                partyService.joinParty("abc123", 10L, "닉", 1L)
            }
        assertEquals(ErrorCode.ALREADY_JOINED, ex.errorCode)
    }

    @Test
    fun `joinParty 존재하지 않는 characterId면 CHARACTER_NOT_FOUND`() {
        val party = newParty()
        whenever(partyRepository.findByShareLink("abc123")).thenReturn(party)
        whenever(characterRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyService.joinParty("abc123", null, "닉", 999L)
            }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }
}
