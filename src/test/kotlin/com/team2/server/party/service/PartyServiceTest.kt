package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
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
    lateinit var userRepository: UserRepository

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

    // --- 파티 생성 ---

    @Test
    fun `createParty PAPER_ONLY는 startedDate를 당일 00시로 저장한다`() {
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
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        partyService.createParty(1L, request, PartyOption.PAPER_ONLY)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertEquals(LocalDate.of(2026, 5, 1).atStartOfDay(), (partyCaptor.firstValue as PaperOnlyParty).startedAt)
    }

    @Test
    fun `createParty REALTIME에서 startTime null이면 예외 발생`() {
        val request =
            CreatePartyRequest(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 5, 1),
                startTime = null,
            )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(newUser(id = 1L)))

        assertThrows<IllegalArgumentException> {
            partyService.createParty(1L, request, PartyOption.REALTIME)
        }
        verify(partyRepository, never()).save(any())
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
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        partyService.createParty(1L, request, PartyOption.REALTIME)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertTrue(partyCaptor.firstValue is RealtimeParty)
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
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        partyService.createParty(1L, request, PartyOption.PAPER_ONLY)

        val partyCaptor = argumentCaptor<Party>()
        verify(partyRepository).save(partyCaptor.capture())
        assertTrue(partyCaptor.firstValue is PaperOnlyParty)
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
        whenever(participantRepository.save(any())).thenAnswer { it.getArgument<Participant>(0) }

        val result = partyService.createParty(1L, request, PartyOption.REALTIME)

        val participantCaptor = argumentCaptor<Participant>()
        val profileCaptor = argumentCaptor<RealtimeParticipantProfile>()
        verify(participantRepository).save(participantCaptor.capture())
        verify(realtimeParticipantProfileRepository).save(profileCaptor.capture())
        val participant = participantCaptor.firstValue
        assertEquals(7L, result.partyId)
        assertEquals(savedParty, participant.party)
        assertEquals(user, participant.user)
        assertTrue(participant.isCelebrant)
        assertEquals(participant, profileCaptor.firstValue.participant)
        assertEquals("홍길동", profileCaptor.firstValue.nickname)
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
}
