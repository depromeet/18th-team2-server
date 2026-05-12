package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyInviteService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ActivateInviteLinkUseCaseTest {
    @Mock
    lateinit var partyInviteService: PartyInviteService

    @InjectMocks
    lateinit var useCase: ActivateInviteLinkUseCase

    @Test
    fun `activate delegates and returns token`() {
        whenever(partyInviteService.activateInviteLink(partyId = 1L, userId = 42L))
            .thenReturn("example-token-0000")

        val token = useCase.activate(partyId = 1L, userId = 42L)

        assertEquals("example-token-0000", token)
        verify(partyInviteService).activateInviteLink(partyId = 1L, userId = 42L)
    }
}
