package com.team2.server.calendar.application.service

import com.team2.server.auth.config.JwtProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ConsentTicketSignerTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val properties = JwtProperties(secret = secret, expirationHours = 24)
    private val issuedAt = LocalDateTime.of(2026, 8, 19, 12, 0)

    private fun signerAt(now: LocalDateTime) =
        ConsentTicketSigner(properties, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

    @Test
    fun `발급한 티켓을 검증하면 userId 가 나온다`() {
        val signer = signerAt(issuedAt)

        assertEquals(42L, signer.verify(signer.issue(42L)))
    }

    @Test
    fun `5분이 지나기 전이면 유효하다`() {
        val ticket = signerAt(issuedAt).issue(42L)

        assertEquals(42L, signerAt(issuedAt.plusSeconds(299)).verify(ticket))
    }

    @Test
    fun `5분이 지나면 만료된다`() {
        val ticket = signerAt(issuedAt).issue(42L)

        assertNull(signerAt(issuedAt.plusSeconds(301)).verify(ticket))
    }

    @Test
    fun `서명이 위조되면 거부한다`() {
        val ticket = signerAt(issuedAt).issue(42L)
        val forged = ticket.dropLast(4) + "AAAA"

        assertNull(signerAt(issuedAt).verify(forged))
    }

    @Test
    fun `userId 를 바꿔치기하면 거부한다`() {
        val signer = signerAt(issuedAt)
        val ticket = signer.issue(42L)
        val payload = String(Base64.getUrlDecoder().decode(ticket.substringBefore('.')))
        val tamperedPayload =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.replace("42", "43").toByteArray())

        assertNull(signer.verify(tamperedPayload + "." + ticket.substringAfter('.')))
    }

    @Test
    fun `형식이 다르면 거부한다`() {
        val signer = signerAt(issuedAt)

        assertNull(signer.verify("not-a-ticket"))
        assertNull(signer.verify(""))
    }

    @Test
    fun `같은 사용자라도 발급할 때마다 다른 티켓이 나온다`() {
        val first = signerAt(issuedAt).issue(42L)
        val second = signerAt(issuedAt.plusSeconds(1)).issue(42L)

        assertEquals(42L, signerAt(issuedAt.plusSeconds(1)).verify(first))
        assertNotEquals(first, second)
    }
}
