package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertSame

@ExtendWith(MockitoExtension::class)
class RealtimeParticipantProfileServiceTest {
    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var profileRepository: RealtimeParticipantProfileRepository

    @InjectMocks
    lateinit var service: RealtimeParticipantProfileService

    private val party =
        PaperOnlyParty(
            ownerId = 1L,
            celebrantNickname = "홍길동",
            startedAt = java.time.LocalDateTime.now(),
        )
    private val character = Character(name = "기본")
    private val anotherCharacter = Character(name = "리본")

    private fun newParticipant(isCelebrant: Boolean): Participant =
        Participant(party = party, isCelebrant = isCelebrant)

    @Test
    fun `기존 프로필이 없으면 새로 생성한다`() {
        val participant = newParticipant(isCelebrant = false)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(null)
        whenever(
            profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                partyId = eq(party.id),
                nickname = eq("안녕"),
                excludingParticipantId = eq(participant.id),
            ),
        ).thenReturn(false)
        whenever(profileRepository.save(any<RealtimeParticipantProfile>())).thenAnswer { it.arguments[0] }

        val result = service.upsert(participant, "안녕", character, isHostNicknameLocked = false)

        val captor = argumentCaptor<RealtimeParticipantProfile>()
        verify(profileRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("안녕", saved.nickname)
        assertSame(character, saved.character)
        assertSame(participant, saved.participant)
        assertEquals("안녕", result.nickname)
    }

    @Test
    fun `기존 프로필이 있으면 nickname과 character를 갱신한다 (locked = false)`() {
        val participant = newParticipant(isCelebrant = false)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "old", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)
        whenever(
            profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                partyId = eq(party.id),
                nickname = eq("new"),
                excludingParticipantId = eq(participant.id),
            ),
        ).thenReturn(false)

        val result = service.upsert(participant, "new", anotherCharacter, isHostNicknameLocked = false)

        verify(profileRepository, never()).save(any<RealtimeParticipantProfile>())
        assertEquals("new", existing.nickname)
        assertSame(anotherCharacter, existing.character)
        assertSame(existing, result)
    }

    @Test
    fun `locked = true이고 nickname이 같으면 character만 갱신한다`() {
        val participant = newParticipant(isCelebrant = true)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "host", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        val result = service.upsert(participant, "host", anotherCharacter, isHostNicknameLocked = true)

        verify(profileRepository, never()).save(any<RealtimeParticipantProfile>())
        assertEquals("host", existing.nickname)
        assertSame(anotherCharacter, existing.character)
        assertSame(existing, result)
    }

    @Test
    fun `locked = true이고 nickname이 다르면 PARTY_HOST_NICKNAME_NOT_EDITABLE`() {
        val participant = newParticipant(isCelebrant = true)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "host", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        val e =
            assertThrows<BusinessException> {
                service.upsert(participant, "different", anotherCharacter, isHostNicknameLocked = true)
            }
        assertEquals(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE, e.errorCode)
        assertEquals("host", existing.nickname)
        assertSame(character, existing.character)
    }

    @Test
    fun `새 프로필 생성 시 같은 파티에 동일 nickname이 있으면 PARTY_NICKNAME_DUPLICATED`() {
        val participant = newParticipant(isCelebrant = false)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(null)
        whenever(
            profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                partyId = eq(party.id),
                nickname = eq("dupe"),
                excludingParticipantId = eq(participant.id),
            ),
        ).thenReturn(true)

        val e =
            assertThrows<BusinessException> {
                service.upsert(participant, "dupe", character, isHostNicknameLocked = false)
            }
        assertEquals(ErrorCode.PARTY_NICKNAME_DUPLICATED, e.errorCode)
        verify(profileRepository, never()).save(any<RealtimeParticipantProfile>())
    }

    @Test
    fun `기존 프로필 갱신 시 nickname이 변경되고 중복이면 PARTY_NICKNAME_DUPLICATED`() {
        val participant = newParticipant(isCelebrant = false)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "old", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)
        whenever(
            profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                partyId = eq(party.id),
                nickname = eq("new"),
                excludingParticipantId = eq(participant.id),
            ),
        ).thenReturn(true)

        val e =
            assertThrows<BusinessException> {
                service.upsert(participant, "new", anotherCharacter, isHostNicknameLocked = false)
            }
        assertEquals(ErrorCode.PARTY_NICKNAME_DUPLICATED, e.errorCode)
        assertEquals("old", existing.nickname)
        assertSame(character, existing.character)
    }

    @Test
    fun `nickname이 변경되지 않으면 중복 검사를 수행하지 않는다`() {
        val participant = newParticipant(isCelebrant = false)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "same", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        service.upsert(participant, "same", anotherCharacter, isHostNicknameLocked = false)

        verify(profileRepository, never()).existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
            any(),
            any(),
            any(),
        )
        assertSame(anotherCharacter, existing.character)
    }
}
