package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.support.JpaSliceTestSupport
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoCalendarConnectionServiceTest
    @Autowired
    constructor(
        private val repository: KakaoCalendarConnectionRepository,
        private val entityManager: EntityManager,
    ) : JpaSliceTestSupport() {
        private val service = KakaoCalendarConnectionService(repository)
        private val now = LocalDateTime.of(2026, 8, 19, 12, 0)

        private fun connection(
            userId: Long = 1L,
            accessExpiresAt: LocalDateTime = now.plusHours(6),
            refreshExpiresAt: LocalDateTime = now.plusMonths(2),
        ) = KakaoCalendarConnection(
            userId = userId,
            accessToken = "access-token-value",
            refreshToken = "refresh-token-value",
            accessTokenExpiresAt = accessExpiresAt,
            refreshTokenExpiresAt = refreshExpiresAt,
        )

        @Test
        fun `연동이 없으면 null 을 반환한다`() {
            assertNull(service.find(userId = 1L))
        }

        @Test
        fun `저장한 연동을 사용자로 조회한다`() {
            service.save(connection())

            val found = service.find(userId = 1L)

            assertEquals("access-token-value", found?.accessToken)
            assertEquals("refresh-token-value", found?.refreshToken)
        }

        @Test
        fun `토큰은 DB 에 평문으로 저장되지 않는다`() {
            service.save(connection())
            entityManager.flush()
            entityManager.clear()

            val stored =
                entityManager
                    .createNativeQuery("select access_token, refresh_token from kakao_calendar_connection")
                    .singleResult as Array<*>

            assertFalse((stored[0] as String).contains("access-token-value"))
            assertFalse((stored[1] as String).contains("refresh-token-value"))
        }

        @Test
        fun `연동을 삭제한다`() {
            service.save(connection())

            service.delete(service.find(userId = 1L)!!)

            assertNull(service.find(userId = 1L))
        }

        @Test
        fun `액세스 토큰 만료가 60초 이상 남았으면 사용 가능하다`() {
            val target = connection(accessExpiresAt = now.plusSeconds(61))

            assertTrue(target.isAccessTokenUsableAt(now))
        }

        @Test
        fun `액세스 토큰 만료가 60초 이하로 남았으면 사용 불가다`() {
            val target = connection(accessExpiresAt = now.plusSeconds(60))

            assertFalse(target.isAccessTokenUsableAt(now))
        }

        @Test
        fun `리프레시 토큰 만료 시각이 지났으면 만료로 본다`() {
            val target = connection(refreshExpiresAt = now)

            assertTrue(target.isRefreshTokenExpiredAt(now))
        }

        @Test
        fun `갱신은 새 액세스 토큰을 반영하고 리프레시 토큰이 없으면 기존 것을 유지한다`() {
            val target = connection()

            target.applyRefreshed(
                accessToken = "new-access",
                accessTokenExpiresAt = now.plusHours(6),
                refreshToken = null,
                refreshTokenExpiresAt = null,
            )

            assertEquals("new-access", target.accessToken)
            assertEquals("refresh-token-value", target.refreshToken)
        }

        @Test
        fun `갱신 응답에 리프레시 토큰이 있으면 교체한다`() {
            val target = connection()

            target.applyRefreshed(
                accessToken = "new-access",
                accessTokenExpiresAt = now.plusHours(6),
                refreshToken = "new-refresh",
                refreshTokenExpiresAt = now.plusMonths(2),
            )

            assertEquals("new-refresh", target.refreshToken)
        }
    }
