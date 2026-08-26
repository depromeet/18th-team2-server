package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 저장된 연동에서 쓸 수 있는 액세스 토큰을 확보한다.
 *
 * 등록 트랜잭션과 분리된 자체 트랜잭션이다. 연동 행을 잠그고 갱신까지 마친 뒤 잠금을 놓으므로,
 * 이어지는 일정 등록은 연동 행 잠금 없이 진행된다.
 *
 * **예외를 던지지 않는다.** 동의가 필요한 상황을 예외로 알리면 이 트랜잭션이 롤백되면서
 * 죽은 연동을 지운 것까지 되감기고, 이후 매 요청이 무효한 리프레시 토큰으로 카카오를 두드리게 된다.
 * 대신 `null` 을 돌려주고 판단은 호출자에게 맡긴다.
 */
@Service
class ResolveKakaoCalendarAccessTokenUseCase(
    private val kakaoCalendarConnectionService: KakaoCalendarConnectionService,
    private val kakaoOAuthPort: KakaoOAuthPort,
    private val clock: Clock,
) {
    @Suppress("ReturnCount")
    @Transactional
    operator fun invoke(userId: Long): String? {
        val now = LocalDateTime.now(clock)
        val connection = kakaoCalendarConnectionService.find(userId) ?: return null

        if (connection.isAccessTokenUsableAt(now)) return connection.accessToken
        if (connection.isRefreshTokenExpiredAt(now)) return disconnect(connection)

        val tokens = kakaoOAuthPort.refresh(connection.refreshToken) ?: return disconnect(connection)
        connection.applyRefreshed(
            accessToken = tokens.accessToken,
            accessTokenExpiresAt = now.plusSeconds(tokens.accessTokenExpiresInSeconds),
            refreshToken = tokens.refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresInSeconds?.let { now.plusSeconds(it) },
        )
        return connection.accessToken
    }

    /** 되살릴 수 없는 연동은 지운다. 죽은 자격증명을 붙들고 있으면 매 요청이 헛돈다. */
    private fun disconnect(connection: KakaoCalendarConnection): String? {
        kakaoCalendarConnectionService.delete(connection)
        return null
    }
}
