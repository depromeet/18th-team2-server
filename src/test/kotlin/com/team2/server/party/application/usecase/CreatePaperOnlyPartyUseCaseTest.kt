package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.service.PartyService
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CreatePaperOnlyPartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @Mock
    lateinit var userRepository: UserRepository

    @Test
    fun `invoke delegates to partyService and returns partyId`() {
        val command =
            CreatePaperOnlyPartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 6, 1),
            )
        val user = user()
        val useCase = CreatePaperOnlyPartyUseCase(partyService, userRepository)
        whenever(userRepository.findById(42L)).thenReturn(Optional.of(user))
        whenever(partyService.createPaperOnlyParty(userId = 42L, user = user, command = command)).thenReturn(101L)

        val partyId = useCase.invoke(userId = 42L, command = command)

        assertEquals(101L, partyId)
        verify(partyService).createPaperOnlyParty(userId = 42L, user = user, command = command)
    }

    private fun user(): User =
        User(
            name = "회원",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "member-42",
            email = "member42@example.com",
        )
}
