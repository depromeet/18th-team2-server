package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.service.PartyCreationService
import com.team2.server.party.application.service.PartyInviteService
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
    lateinit var partyCreationService: PartyCreationService

    @Mock
    lateinit var partyInviteService: PartyInviteService

    @Mock
    lateinit var userRepository: UserRepository

    @Test
    fun `invoke delegates to partyCreationService and returns partyId`() {
        val command =
            CreatePaperOnlyPartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 6, 1),
            )
        val user = user()
        val useCase = CreatePaperOnlyPartyUseCase(partyCreationService, partyInviteService, userRepository)
        whenever(userRepository.findById(42L)).thenReturn(Optional.of(user))
        whenever(
            partyCreationService.createPaperOnlyParty(userId = 42L, user = user, command = command),
        ).thenReturn(101L)
        whenever(partyInviteService.activateInviteLink(partyId = 101L, userId = 42L)).thenReturn("invite-token")

        val partyId = useCase.invoke(userId = 42L, command = command)

        assertEquals(101L, partyId)
        verify(partyCreationService).createPaperOnlyParty(userId = 42L, user = user, command = command)
        verify(partyInviteService).activateInviteLink(partyId = 101L, userId = 42L)
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
