package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.service.PartyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CreatePaperOnlyPartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @InjectMocks
    lateinit var useCase: CreatePaperOnlyPartyUseCase

    @Test
    fun `invoke delegates to partyService and returns partyId`() {
        val command =
            CreatePaperOnlyPartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 6, 1),
            )
        whenever(partyService.createPaperOnlyParty(userId = 42L, command = command)).thenReturn(101L)

        val partyId = useCase.invoke(userId = 42L, command = command)

        assertEquals(101L, partyId)
        verify(partyService).createPaperOnlyParty(userId = 42L, command = command)
    }
}
