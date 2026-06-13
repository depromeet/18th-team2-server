package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertSame

@ExtendWith(MockitoExtension::class)
class ResolveLiveOpenRealtimePartyUseCaseTest {
    @Mock lateinit var partyService: PartyService

    private val clock = Clock.fixed(Instant.parse("2026-06-08T11:00:30Z"), ZoneId.of("Asia/Seoul"))
    private lateinit var useCase: ResolveLiveOpenRealtimePartyUseCase

    @BeforeEach
    fun setUp() {
        useCase = ResolveLiveOpenRealtimePartyUseCase(partyService, clock)
    }

    @Test
    fun `LIVE_OPEN이면 실시간 기능 사용 가능 파티를 반환한다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now().minusMinutes(1))
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val result = useCase.invoke(1L)

        assertSame(party, result)
    }

    @Test
    fun `LIVE_ENDING이면 실시간 기능 사용 가능 파티를 반환한다`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                startedAt = now().minusMinutes(1),
                liveEndingStartedAt = now().minusSeconds(10),
            )
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val result = useCase.invoke(1L)

        assertSame(party, result)
    }

    @Test
    fun `LIVE_CLOSED이면 CHAT_NOT_ACTIVE`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                startedAt = now().minusMinutes(1),
                liveEndingStartedAt = now().minusSeconds(61),
            )
        whenever(partyService.requireRealtimeParty(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> { useCase.invoke(1L) }

        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)
}
