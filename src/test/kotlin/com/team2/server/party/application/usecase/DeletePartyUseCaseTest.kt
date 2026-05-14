package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class DeletePartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @InjectMocks
    lateinit var useCase: DeletePartyUseCase

    @Test
    fun `delete delegates to partyService`() {
        useCase.delete(partyId = 1L, userId = 42L)
        verify(partyService).deleteParty(partyId = 1L, userId = 42L)
    }
}
