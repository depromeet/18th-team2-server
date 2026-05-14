package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.service.PartyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CreateRealtimePartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @InjectMocks
    lateinit var useCase: CreateRealtimePartyUseCase

    @Test
    fun `invoke delegates to partyService and returns partyId`() {
        val command =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 6, 1),
                startTime = LocalTime.of(20, 0),
                characterId = 1L,
            )
        whenever(partyService.createRealtimeParty(userId = 42L, command = command)).thenReturn(100L)

        val partyId = useCase.invoke(userId = 42L, command = command)

        assertEquals(100L, partyId)
        verify(partyService).createRealtimeParty(userId = 42L, command = command)
    }
}
