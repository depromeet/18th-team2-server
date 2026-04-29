package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
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
    lateinit var partyInviteRepository: PartyInviteRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var characterImageUrlResolver: CharacterImageUrlResolver

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

    // --- 파티 생성 ---

    @Test
    fun `createParty startTime이 null이면 00시로 저장됨`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 9L)
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 5, 1),
                startTime = null,
            )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(partyRepository.save(any())).thenReturn(savedParty)

        partyService.createParty(1L, request, PartyOption.PAPER_ONLY)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertEquals(LocalDate.of(2026, 5, 1).atStartOfDay(), partyCaptor.firstValue.startedAt)
    }

    @Test
    fun `createParty REALTIME 옵션으로 생성하면 REALTIME이 저장됨`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
            )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(partyRepository.save(any())).thenReturn(savedParty)

        partyService.createParty(1L, request, PartyOption.REALTIME)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertEquals(PartyOption.REALTIME, partyCaptor.firstValue.option)
    }

    @Test
    fun `createParty PAPER_ONLY 옵션으로 생성하면 PAPER_ONLY가 저장됨`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
            )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(partyRepository.save(any())).thenReturn(savedParty)

        partyService.createParty(1L, request, PartyOption.PAPER_ONLY)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertEquals(PartyOption.PAPER_ONLY, partyCaptor.firstValue.option)
    }

    @Test
    fun `createParty 파티 생성 시 주최자를 참여자로 저장`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
            )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(partyRepository.save(any())).thenReturn(savedParty)

        val result = partyService.createParty(1L, request, PartyOption.REALTIME)

        val participantCaptor = argumentCaptor<Participant>()
        verify(participantRepository).save(participantCaptor.capture())
        val participant = participantCaptor.firstValue
        assertEquals(7L, result.partyId)
        assertEquals(savedParty, participant.party)
        assertEquals(user, participant.user)
        assertEquals("홍길동", participant.nickname)
        assertTrue(participant.isCelebrant)
    }

    @Test
    fun `createParty 유저가 없으면 AUTH_USER_NOT_FOUND`() {
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
            )
        whenever(userRepository.findById(1L)).thenReturn(Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyService.createParty(1L, request, PartyOption.REALTIME)
            }

        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.errorCode)
        verify(partyRepository, never()).save(any())
        verify(participantRepository, never()).save(any())
    }

    // --- 파티 정보 조회 ---

    @Test
    fun `getPartyInfo 존재하는 파티 정보 반환`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val result = partyService.getPartyInfo("abc123", null)

        assertEquals("생일파티", result.name)
        assertEquals("홍길동", result.celebrantNickname)
        assertEquals(PartyPurpose.BIRTHDAY, result.purpose)
        assertEquals(PartyOption.REALTIME, result.option)
        assertFalse(result.ended)
        assertNull(result.myParticipant)
    }

    @Test
    fun `getPartyInfo 종료된 파티도 정상 반환하고 ended가 true`() {
        val party = newParty(endedAt = LocalDateTime.now().minusDays(1))
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))

        val result = partyService.getPartyInfo("abc123", null)

        assertTrue(result.ended)
    }

    @Test
    fun `getPartyInfo 존재하지 않는 shareLink면 PARTY_NOT_FOUND`() {
        whenever(partyInviteRepository.findByToken("no-link")).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyService.getPartyInfo("no-link", null)
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `getPartyInfo 만료된 초대 토큰이면 INVITE_LINK_EXPIRED`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("expired")).thenReturn(
            newInvite(token = "expired", party = party, expiresAt = LocalDateTime.now().minusSeconds(1)),
        )

        val ex =
            assertThrows<BusinessException> {
                partyService.getPartyInfo("expired", null)
            }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `getPartyInfo 만료 시간이 현재와 같으면 INVITE_LINK_EXPIRED`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("expired")).thenReturn(
            newInvite(token = "expired", party = party, expiresAt = LocalDateTime.now()),
        )

        val ex =
            assertThrows<BusinessException> {
                partyService.getPartyInfo("expired", null)
            }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
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

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.findByPartyAndUser(party, user)).thenReturn(participant)
        whenever(characterImageUrlResolver.resolve(character)).thenReturn("/images/characters/character1.jpg")

        val result = partyService.getPartyInfo("abc123", 10L)

        val myParticipant = assertNotNull(result.myParticipant)
        assertEquals(5L, myParticipant.participantId)
        assertEquals("닉네임", myParticipant.nickname)
        assertEquals("/images/characters/character1.jpg", myParticipant.characterImageUrl)
    }

    @Test
    fun `getPartyInfo 회원이 미참여면 myParticipant가 null`() {
        val party = newParty()
        val user = newUser()

        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user))
        whenever(participantRepository.findByPartyAndUser(party, user)).thenReturn(null)

        val result = partyService.getPartyInfo("abc123", 10L)

        assertNull(result.myParticipant)
    }

    @Test
    fun `getPartyInfo 인증 userId가 DB에 없으면 AUTH_USER_NOT_FOUND`() {
        val party = newParty()
        whenever(partyInviteRepository.findByToken("abc123")).thenReturn(newInvite(party = party))
        whenever(userRepository.findById(10L)).thenReturn(java.util.Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyService.getPartyInfo("abc123", 10L)
            }

        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.errorCode)
    }
}
