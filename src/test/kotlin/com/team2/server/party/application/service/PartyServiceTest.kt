package com.team2.server.party.application.service

import com.team2.server.chat.repository.ChatMessageRepository
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
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
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
import org.mockito.kotlin.inOrder
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
class PartyServiceTest {
    @Mock
    lateinit var partyRepository: PartyRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository

    @Mock
    lateinit var characterRepository: CharacterRepository

    @Mock
    lateinit var partyInviteRepository: PartyInviteRepository

    @Mock
    lateinit var chatMessageRepository: ChatMessageRepository

    @Mock
    lateinit var rollingPaperRepository: RollingPaperRepository

    @InjectMocks
    lateinit var partyService: PartyService

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
        name: String = "Default",
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

        partyService.createPaperOnlyParty(1L, user, request)

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

        partyService.createRealtimeParty(1L, user, request)

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

        partyService.createPaperOnlyParty(1L, user, request)

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

        val result = partyService.createRealtimeParty(1L, user, request)

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
        val character = newCharacter(2L, "Girl")
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

        partyService.createRealtimeParty(1L, user, request)

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
                partyService.createRealtimeParty(1L, user, request)
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
                partyService.createRealtimeParty(userId = 1L, user = user, command = request)
            }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
        verify(partyRepository, never()).save(any())
        verify(participantRepository, never()).save(any())
    }

    // --- 파티 삭제 ---

    @Test
    fun `deleteParty 파티가 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(99L)).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 99L, userId = 1L)
            }

        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 주최자가 아니면 PARTY_FORBIDDEN`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 10L)
        whenever(partyRepository.findPartyById(10L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 10L, userId = 999L)
            }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 파티가 이미 시작됐으면 PARTY_ALREADY_STARTED`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().minusMinutes(1),
            )
        setId(party, 10L)
        whenever(partyRepository.findPartyById(10L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 10L, userId = 1L)
            }

        assertEquals(ErrorCode.PARTY_ALREADY_STARTED, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 정상 삭제 시 연관 데이터를 순서대로 삭제한다`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 10L)

        val participant1 = Participant(party = party, user = null, isCelebrant = true)
        setId(participant1, 100L)
        val participant2 = Participant(party = party, user = null, isCelebrant = false)
        setId(participant2, 101L)

        whenever(partyRepository.findPartyById(10L)).thenReturn(party)
        whenever(participantRepository.findAllByPartyId(10L))
            .thenReturn(listOf(participant1, participant2))

        partyService.deleteParty(partyId = 10L, userId = 1L)

        val order =
            inOrder(
                chatMessageRepository,
                rollingPaperRepository,
                realtimeParticipantProfileRepository,
                participantRepository,
                partyInviteRepository,
                partyRepository,
            )
        order.verify(chatMessageRepository).deleteAllByPartyId(10L)
        order.verify(rollingPaperRepository).deleteAllByPartyId(10L)
        order
            .verify(realtimeParticipantProfileRepository)
            .deleteAllByParticipantIdIn(listOf(100L, 101L))
        order.verify(participantRepository).deleteAll(listOf(participant1, participant2))
        order.verify(partyInviteRepository).deleteAllByPartyId(10L)
        order.verify(partyRepository).delete(party)
    }

    @Test
    fun `deleteParty PAPER_ONLY 파티 삭제 시 realtimeParticipantProfile을 삭제하지 않는다`() {
        val party =
            PaperOnlyParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 20L)

        whenever(partyRepository.findPartyById(20L)).thenReturn(party)
        whenever(participantRepository.findAllByPartyId(20L)).thenReturn(emptyList())

        partyService.deleteParty(partyId = 20L, userId = 1L)

        verify(realtimeParticipantProfileRepository, never()).deleteAllByParticipantIdIn(any())
    }

    // --- findActiveRealtimeParty ---

    @Test
    fun `findActiveRealtimeParty returns realtime party when live open`() {
        val realtimeParty =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().minusSeconds(1),
            )
        whenever(partyRepository.findPartyById(10L)).thenReturn(realtimeParty)

        val result = partyService.findActiveRealtimeParty(10L)

        assertEquals(realtimeParty, result)
    }

    @Test
    fun `findActiveRealtimeParty throws PARTY_NOT_FOUND when party absent`() {
        whenever(partyRepository.findPartyById(10L)).thenReturn(null)
        val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `findActiveRealtimeParty throws CHAT_NOT_SUPPORTED when paper only`() {
        val paperOnly =
            PaperOnlyParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now(),
            )
        whenever(partyRepository.findPartyById(10L)).thenReturn(paperOnly)
        val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, e.errorCode)
    }

    @Test
    fun `findActiveRealtimeParty throws CHAT_NOT_ACTIVE when not in live window`() {
        val realtimeParty =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusHours(1),
            )
        whenever(partyRepository.findPartyById(10L)).thenReturn(realtimeParty)
        val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, e.errorCode)
    }
}
