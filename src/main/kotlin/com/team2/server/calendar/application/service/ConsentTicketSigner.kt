package com.team2.server.calendar.application.service

import com.team2.server.auth.config.JwtProperties
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val ALGORITHM = "HmacSHA256"

/**
 * 동의 플로우의 `state` 값을 만들고 검증한다.
 *
 * 티켓은 `base64url(userId:발급시각) . base64url(HMAC)` 형태다. 요청자를 콜백까지 나르는 것이 목적이고,
 * 콜백에서 카카오가 확인해 준 계정과 대조해 엉뚱한 사용자 행에 토큰이 저장되는 것을 막는다.
 *
 * 서명 키로 JWT 시크릿을 재사용한다. 용도가 같은 HMAC 서명이고, 티켓은 5분짜리라 키가 교체돼도
 * 잃을 것이 없기 때문이다. 암호화 키를 따로 둔 것과는 다른 상황이다.
 */
@Service
class ConsentTicketSigner(
    jwtProperties: JwtProperties,
    private val clock: Clock,
) {
    private val key = SecretKeySpec(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)

    fun issue(userId: Long): String {
        val payload = "$userId:${Instant.now(clock).epochSecond}"
        val encodedPayload = encode(payload.toByteArray(StandardCharsets.UTF_8))
        return "$encodedPayload.${encode(sign(encodedPayload))}"
    }

    @Suppress("ReturnCount")
    fun verify(ticket: String): Long? {
        val encodedPayload = ticket.substringBefore('.', missingDelimiterValue = "")
        val encodedSignature = ticket.substringAfter('.', missingDelimiterValue = "")
        if (encodedPayload.isEmpty() || encodedSignature.isEmpty()) return null

        val expected = encode(sign(encodedPayload))
        if (!MessageDigest.isEqual(expected.toByteArray(), encodedSignature.toByteArray())) return null

        val payload =
            runCatching { String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8) }
                .getOrNull() ?: return null
        val userId = payload.substringBefore(':').toLongOrNull() ?: return null
        val issuedAtEpochSecond = payload.substringAfter(':').toLongOrNull() ?: return null

        val elapsed = Instant.now(clock).epochSecond - issuedAtEpochSecond
        if (elapsed < 0 || elapsed > TTL_SECONDS) return null
        return userId
    }

    private fun sign(encodedPayload: String): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(encodedPayload.toByteArray(StandardCharsets.UTF_8))

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        const val TTL_SECONDS = 300L
    }
}
