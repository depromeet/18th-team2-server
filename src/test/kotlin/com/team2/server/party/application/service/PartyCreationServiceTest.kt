package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyPurpose
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class PartyCreationServiceTest {
    @Mock
    lateinit var partyRepository: PartyRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository

    @Mock
    lateinit var characterRepository: CharacterRepository

    @InjectMocks
    lateinit var partyCreationService: PartyCreationService

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val idField = clazz.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(entity, id)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("id not found in class hierarchy of ${entity.javaClass.name}")
    }

    private fun newParty(id: Long = 1L): RealtimeParty {
        val party =
            RealtimeParty(
                ownerId = 1L,
                name = "생일파티",
                celebrantNickname = "홍길동",
                purpose = PartyPurpose.BIRTHDAY,
                startedAt = LocalDateTime.now(),
            )
        setId(party, id)
        return party
    }

    private fun newCharacter(
        id: Long = 1L,
        name: String = "blue",
    ): Character {
        val character = Character(name = name)
        setId(character, id)
        return character
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

    private fun newPaperOnlyParty(id: Long = 1L): PaperOnlyParty {
        val party =
            PaperOnlyParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now(),
            )
        setId(party, id)
        return party
    }

    // --- 파티 생성 ---

    @Test
    fun `createPaperOnlyParty startedDate를 당일 00시로 저장한다`() {
        val user = newUser(id = 1L)
        val savedParty = newPaperOnlyParty(id = 9L)
        val request =
            CreatePaperOnlyPartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 5, 1),
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        partyCreationService.createPaperOnlyParty(1L, user, request)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertEquals(LocalDate.of(2026, 5, 1).atStartOfDay(), (partyCaptor.firstValue as PaperOnlyParty).startedAt)
        verify(realtimeParticipantProfileRepository, never()).save(any())
    }

    @Test
    fun `createRealtimeParty REALTIME이 저장됨`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val character = newCharacter(1L)
        val request =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
                characterId = 1L,
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }
        whenever(characterRepository.findById(1L)).thenReturn(Optional.of(character))

        partyCreationService.createRealtimeParty(1L, user, request)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertTrue(partyCaptor.firstValue is RealtimeParty)
    }

    @Test
    fun `createPaperOnlyParty PAPER_ONLY가 저장됨`() {
        val user = newUser(id = 1L)
        val savedParty = newPaperOnlyParty(id = 7L)
        val request =
            CreatePaperOnlyPartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        partyCreationService.createPaperOnlyParty(1L, user, request)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertTrue(partyCaptor.firstValue is PaperOnlyParty)
        verify(realtimeParticipantProfileRepository, never()).save(any())
    }

    @Test
    fun `createRealtimeParty 파티 생성 시 주최자를 참여자로 저장`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val character = newCharacter(1L)
        val request =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
                characterId = 1L,
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }
        whenever(characterRepository.findById(1L)).thenReturn(Optional.of(character))

        val result = partyCreationService.createRealtimeParty(1L, user, request)

        val participantCaptor = argumentCaptor<Participant>()
        val profileCaptor = argumentCaptor<RealtimeParticipantProfile>()
        verify(participantRepository).save(participantCaptor.capture())
        verify(realtimeParticipantProfileRepository).save(profileCaptor.capture())
        val participant = participantCaptor.firstValue
        assertEquals(7L, result)
        assertEquals(savedParty, participant.party)
        assertEquals(user, participant.user)
        assertTrue(participant.isCelebrant)
        assertEquals(participant, profileCaptor.firstValue.participant)
        assertEquals("홍길동", profileCaptor.firstValue.nickname)
        assertEquals(character, profileCaptor.firstValue.character)
    }

    @Test
    fun `createRealtimeParty 요청한 characterId의 캐릭터가 할당됨`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val character = newCharacter(2L, "green")
        val request =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
                characterId = 2L,
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }
        whenever(characterRepository.findById(2L)).thenReturn(Optional.of(character))

        partyCreationService.createRealtimeParty(1L, user, request)

        val profileCaptor = argumentCaptor<RealtimeParticipantProfile>()
        verify(realtimeParticipantProfileRepository).save(profileCaptor.capture())
        assertEquals(character, profileCaptor.firstValue.character)
    }

    @Test
    fun `createRealtimeParty 존재하지 않는 characterId이면 CHARACTER_NOT_FOUND`() {
        val user = newUser(id = 1L)
        val savedParty = newParty(id = 7L)
        val request =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
                characterId = 999L,
            )

        whenever(partyRepository.save(any())).thenReturn(savedParty)
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }
        whenever(characterRepository.findById(999L)).thenReturn(Optional.empty())

        val ex =
            assertThrows<BusinessException> {
                partyCreationService.createRealtimeParty(1L, user, request)
            }

        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
        verify(realtimeParticipantProfileRepository, never()).save(any())
    }

    @Test
    fun `createRealtimeParty ownerId와 user id가 다르면 저장하지 않는다`() {
        val user = newUser(id = 2L)
        val request =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 4, 29),
                startTime = LocalTime.of(14, 30),
                characterId = 1L,
            )

        val ex =
            assertThrows<BusinessException> {
                partyCreationService.createRealtimeParty(userId = 1L, user = user, command = request)
            }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
        verify(partyRepository, never()).save(any())
        verify(participantRepository, never()).save(any())
    }
}
