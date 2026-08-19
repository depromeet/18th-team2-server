package com.team2.server.calendar.domain.entity

import com.team2.server.common.persistence.BaseEntity
import com.team2.server.common.security.EncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "kakao_calendar_connection",
    uniqueConstraints = [
        UniqueConstraint(
            name = KakaoCalendarConnection.UK_USER,
            columnNames = ["user_id"],
        ),
    ],
)
class KakaoCalendarConnection(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    accessToken: String,
    refreshToken: String,
    accessTokenExpiresAt: LocalDateTime,
    refreshTokenExpiresAt: LocalDateTime,
) : BaseEntity() {
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "access_token", nullable = false, length = 1024)
    final var accessToken: String = accessToken
        private set

    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "refresh_token", nullable = false, length = 1024)
    final var refreshToken: String = refreshToken
        private set

    @Column(name = "access_token_expires_at", nullable = false)
    final var accessTokenExpiresAt: LocalDateTime = accessTokenExpiresAt
        private set

    @Column(name = "refresh_token_expires_at", nullable = false)
    final var refreshTokenExpiresAt: LocalDateTime = refreshTokenExpiresAt
        private set

    /** 호출 도중 만료되는 경계를 피하려고 여유를 둔다. */
    fun isAccessTokenUsableAt(now: LocalDateTime): Boolean =
        accessTokenExpiresAt.isAfter(now.plusSeconds(ACCESS_TOKEN_LEEWAY_SECONDS))

    fun isRefreshTokenExpiredAt(now: LocalDateTime): Boolean = !refreshTokenExpiresAt.isAfter(now)

    /**
     * 갱신 결과를 반영한다.
     * 카카오는 리프레시 토큰 만료가 1달 이내로 남았을 때만 새 리프레시 토큰을 함께 주므로,
     * 오지 않으면 기존 것을 그대로 쓴다.
     */
    fun applyRefreshed(
        accessToken: String,
        accessTokenExpiresAt: LocalDateTime,
        refreshToken: String?,
        refreshTokenExpiresAt: LocalDateTime?,
    ) {
        this.accessToken = accessToken
        this.accessTokenExpiresAt = accessTokenExpiresAt
        if (refreshToken != null && refreshTokenExpiresAt != null) {
            this.refreshToken = refreshToken
            this.refreshTokenExpiresAt = refreshTokenExpiresAt
        }
    }

    companion object {
        const val ACCESS_TOKEN_LEEWAY_SECONDS = 60L
        const val UK_USER = "uk_kakao_calendar_connection_user"
    }
}
