package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveKakaoCalendarAccessTokenUseCaseTest {
    private val connectionService: KakaoCalendarConnectionService = mock()
    private val kakaoOAuthPort: KakaoOAuthPort = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 19, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = ResolveKakaoCalendarAccessTokenUseCase(connectionService, kakaoOAuthPort, clock)

    private fun connection(
        accessExpiresAt: LocalDateTime = fixedNow.plusHours(6),
        refreshExpiresAt: LocalDateTime = fixedNow.plusMonths(2),
    ) = KakaoCalendarConnection(
        userId = 10L,
        accessToken = "stored-access",
        refreshToken = "stored-refresh",
        accessTokenExpiresAt = accessExpiresAt,
        refreshTokenExpiresAt = refreshExpiresAt,
    )

    @Test
    fun `연동이 없으면 null 을 반환한다`() {
        whenever(connectionService.find(10L)).thenReturn(null)

        assertNull(useCase(10L))
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `액세스 토큰이 유효하면 그대로 쓴다`() {
        whenever(connectionService.find(10L)).thenReturn(connection())

        assertEquals("stored-access", useCase(10L))
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `액세스 토큰이 만료 임박이면 갱신한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh"))
            .thenReturn(KakaoOAuthTokens("new-access", 21599L, null, null))

        assertEquals("new-access", useCase(10L))
        assertEquals(fixedNow.plusSeconds(21599), target.accessTokenExpiresAt)
        verify(connectionService, never()).delete(any())
    }

    @Test
    fun `갱신 응답에 리프레시 토큰이 오면 함께 반영한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh"))
            .thenReturn(KakaoOAuthTokens("new-access", 21599L, "new-refresh", 5183999L))

        useCase(10L)

        assertEquals("new-refresh", target.refreshToken)
        assertEquals(fixedNow.plusSeconds(5183999), target.refreshTokenExpiresAt)
    }

    @Test
    fun `리프레시 토큰이 만료됐으면 연동을 지우고 null 을 반환한다`() {
        val target = connection(accessExpiresAt = fixedNow, refreshExpiresAt = fixedNow)
        whenever(connectionService.find(10L)).thenReturn(target)

        assertNull(useCase(10L))
        verify(connectionService).delete(target)
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `카카오가 갱신을 거부하면 연동을 지우고 null 을 반환한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh")).thenReturn(null)

        assertNull(useCase(10L))
        verify(connectionService).delete(target)
    }
}
