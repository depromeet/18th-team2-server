package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class SaveKakaoCalendarConsentUseCaseTest {
    private val kakaoOAuthPort: KakaoOAuthPort = mock()
    private val kakaoAccountPort: KakaoAccountPort = mock()
    private val calendarUserPort: CalendarUserPort = mock()
    private val connectionService: KakaoCalendarConnectionService = mock()
    private val urlFactory: KakaoConsentUrlFactory = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 19, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        SaveKakaoCalendarConsentUseCase(
            kakaoOAuthPort,
            kakaoAccountPort,
            calendarUserPort,
            connectionService,
            urlFactory,
            clock,
        )

    private val tokens = KakaoOAuthTokens("access-1", 21599L, "refresh-1", 5183999L)

    private fun stubHappyPath() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn("kakao-1")
        whenever(calendarUserPort.findUserIdByKakaoProviderId("kakao-1")).thenReturn(10L)
    }

    @Test
    fun `정상 동의면 연동을 저장하고 GRANTED 를 반환한다`() {
        stubHappyPath()
        whenever(connectionService.find(10L)).thenReturn(null)

        assertEquals(ConsentOutcome.GRANTED, useCase("code-1", ticketUserId = 10L))

        val captor = argumentCaptor<KakaoCalendarConnection>()
        verify(connectionService).save(captor.capture())
        assertEquals(10L, captor.firstValue.userId)
        assertEquals("access-1", captor.firstValue.accessToken)
        assertEquals(fixedNow.plusSeconds(21599), captor.firstValue.accessTokenExpiresAt)
        assertEquals(fixedNow.plusSeconds(5183999), captor.firstValue.refreshTokenExpiresAt)
    }

    @Test
    fun `이미 연동이 있으면 기존 행을 갱신한다`() {
        stubHappyPath()
        val existing =
            KakaoCalendarConnection(
                userId = 10L,
                accessToken = "old-access",
                refreshToken = "old-refresh",
                accessTokenExpiresAt = fixedNow,
                refreshTokenExpiresAt = fixedNow,
            )
        whenever(connectionService.find(10L)).thenReturn(existing)

        assertEquals(ConsentOutcome.GRANTED, useCase("code-1", ticketUserId = 10L))

        assertEquals("access-1", existing.accessToken)
        assertEquals("refresh-1", existing.refreshToken)
        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정이 티켓의 사용자와 다르면 저장하지 않는다`() {
        stubHappyPath()

        assertEquals(ConsentOutcome.ACCOUNT_MISMATCH, useCase("code-1", ticketUserId = 99L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정에 대응하는 사용자가 없으면 저장하지 않는다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn("kakao-1")
        whenever(calendarUserPort.findUserIdByKakaoProviderId("kakao-1")).thenReturn(null)

        assertEquals(ConsentOutcome.ACCOUNT_MISMATCH, useCase("code-1", ticketUserId = 10L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `토큰 교환이 거부되면 FAILED 를 반환한다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(null)

        assertEquals(ConsentOutcome.FAILED, useCase("code-1", ticketUserId = 10L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정 조회가 실패하면 FAILED 를 반환한다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn(null)

        assertEquals(ConsentOutcome.FAILED, useCase("code-1", ticketUserId = 10L))
    }
}
