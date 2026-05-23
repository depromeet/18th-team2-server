package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.event.RealtimePartyCreatedEvent
import com.team2.server.party.application.service.PartyService
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CreateRealtimePartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    lateinit var useCase: CreateRealtimePartyUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            CreateRealtimePartyUseCase(
                partyService = partyService,
                userRepository = userRepository,
                applicationEventPublisher = applicationEventPublisher,
            )
    }

    @Test
    fun `invoke delegates to partyService and returns partyId`() {
        val command =
            CreateRealtimePartyCommand(
                celebrantNickname = "홍길동",
                startedDate = LocalDate.of(2026, 6, 1),
                startTime = LocalTime.of(20, 0),
                characterId = 1L,
            )
        val user = user()
        whenever(userRepository.findById(42L)).thenReturn(Optional.of(user))
        whenever(partyService.createRealtimeParty(userId = 42L, user = user, command = command)).thenReturn(100L)

        val partyId = useCase.invoke(userId = 42L, command = command)

        assertEquals(100L, partyId)
        verify(partyService).createRealtimeParty(userId = 42L, user = user, command = command)
        verify(applicationEventPublisher).publishEvent(
            RealtimePartyCreatedEvent(
                partyId = 100L,
                startedAt = LocalDate.of(2026, 6, 1).atTime(LocalTime.of(20, 0)),
            ),
        )
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
