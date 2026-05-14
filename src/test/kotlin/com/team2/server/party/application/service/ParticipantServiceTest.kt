package com.team2.server.party.application.service

import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ParticipantServiceTest {
    private val participantRepository: ParticipantRepository = mock()
    private val service = ParticipantService(participantRepository)

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
}
