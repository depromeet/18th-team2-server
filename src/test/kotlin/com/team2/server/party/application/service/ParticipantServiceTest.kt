package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParticipantServiceTest {
    private val participantRepository: ParticipantRepository = mock()
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository = mock()
    private val userRepository: UserRepository = mock()
    private val service =
        ParticipantService(
            participantRepository,
            realtimeParticipantProfileRepository,
            userRepository,
        )

    private fun makeUser(): User =
        User(
            name = "테스터",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "kakao-123",
            email = "tester@example.com",
        )

    private fun makeParty(): PaperOnlyParty =
        PaperOnlyParty(
            ownerId = 1L,
            celebrantNickname = "홍길동",
            startedAt = LocalDateTime.now().plusDays(1),
        )

    private fun makeRealtimeParty(): RealtimeParty =
        RealtimeParty(
            ownerId = 1L,
            celebrantNickname = "실시간",
            startedAt = LocalDateTime.now().plusHours(1),
        )

    @Test
    fun `findOrCreate returns existing participant when present`() {
        val party = makeParty()
        val user = makeUser()
        val existing = Participant(party = party, user = user)
        whenever(participantRepository.findByPartyIdAndUserId(party.id, 42L)).thenReturn(existing)

        val result = service.findOrCreate(party, userId = 42L, user = user)

        assertEquals(existing, result)
    }

    @Test
    fun `findOrCreate saves new participant when absent`() {
        val party = makeParty()
        val user = makeUser()
        whenever(participantRepository.findByPartyIdAndUserId(party.id, 42L)).thenReturn(null)
        whenever(participantRepository.save(any<Participant>())).thenAnswer { it.arguments[0] }

        service.findOrCreate(party, userId = 42L, user = user)

        verify(participantRepository).save(any<Participant>())
    }

    @Test
    fun `requireCallerParticipantId returns participant id when JWT user is a participant`() {
        val party = makeRealtimeParty()
        val user = makeUser()
        val participant = Participant(party = party, user = user)
        whenever(participantRepository.findByPartyIdAndUserId(party.id, 42L)).thenReturn(participant)

        val result = service.requireCallerParticipantId(party.id, userId = 42L, participantToken = null)

        assertEquals(participant.id, result)
    }

    @Test
    fun `requireCallerParticipantId throws PARTY_FORBIDDEN when JWT user is not a participant`() {
        val party = makeRealtimeParty()
        whenever(participantRepository.findByPartyIdAndUserId(party.id, 42L)).thenReturn(null)

        val ex =
            assertFailsWith<BusinessException> {
                service.requireCallerParticipantId(party.id, userId = 42L, participantToken = null)
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `requireCallerParticipantId returns participant id when participant token matches the party`() {
        val party = makeRealtimeParty()
        val participant = Participant(party = party, user = null)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "익명")
        whenever(realtimeParticipantProfileRepository.findByParticipantToken("tok")).thenReturn(profile)

        val result = service.requireCallerParticipantId(party.id, userId = null, participantToken = "tok")

        assertEquals(participant.id, result)
    }

    @Test
    fun `requireCallerParticipantId throws PARTY_FORBIDDEN when token belongs to a different party`() {
        val otherParty: Party = mock()
        whenever(otherParty.id).thenReturn(99L)
        val participant: Participant = mock()
        whenever(participant.party).thenReturn(otherParty)
        val profile: RealtimeParticipantProfile = mock()
        whenever(profile.participant).thenReturn(participant)
        whenever(realtimeParticipantProfileRepository.findByParticipantToken("tok")).thenReturn(profile)

        val ex =
            assertFailsWith<BusinessException> {
                service.requireCallerParticipantId(partyId = 1L, userId = null, participantToken = "tok")
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `requireCallerParticipantId throws PARTY_FORBIDDEN when participant token does not exist`() {
        val party = makeRealtimeParty()
        whenever(realtimeParticipantProfileRepository.findByParticipantToken("tok")).thenReturn(null)

        val ex =
            assertFailsWith<BusinessException> {
                service.requireCallerParticipantId(party.id, userId = null, participantToken = "tok")
            }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `requireCallerParticipantId throws UNAUTHORIZED when both userId and token are null`() {
        val party = makeRealtimeParty()

        val ex =
            assertFailsWith<BusinessException> {
                service.requireCallerParticipantId(party.id, userId = null, participantToken = null)
            }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }
}
