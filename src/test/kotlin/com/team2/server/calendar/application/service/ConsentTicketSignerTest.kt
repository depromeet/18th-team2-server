package com.team2.server.calendar.application.service

import com.team2.server.auth.config.JwtProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConsentTicketSignerTest {
    private val fixedInstant: Instant = Instant.parse("2026-10-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val properties =
        JwtProperties(
            secret = "test-secret-key-for-consent-ticket-signer-0123456789",
            expirationHours = 24,
        )
    private val signer = ConsentTicketSigner(properties, clock)

    @Test
    fun `회원 티켓은 사용자 번호로 복원된다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))

        assertEquals(ConsentSubject.Member(53L), signer.verify(ticket))
    }

    @Test
    fun `비회원 티켓은 초대 토큰으로 복원된다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))

        assertEquals(ConsentSubject.Guest("a796ba5f"), signer.verify(ticket))
    }

    @Test
    fun `회원 티켓은 비회원 주체로 읽히지 않는다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))

        assertIs<ConsentSubject.Member>(signer.verify(ticket))
    }

    @Test
    fun `비회원 티켓은 회원 주체로 읽히지 않는다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))

        assertIs<ConsentSubject.Guest>(signer.verify(ticket))
    }

    @Test
    fun `서명이 변조된 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))
        val tampered = ticket.substringBefore('.') + ".AAAA"

        assertNull(signer.verify(tampered))
    }

    @Test
    fun `payload 가 변조된 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Member(53L))
        val forged = signer.issue(ConsentSubject.Member(99L))
        val mixed = forged.substringBefore('.') + "." + ticket.substringAfter('.')

        assertNull(signer.verify(mixed))
    }

    @Test
    fun `수명이 지난 티켓은 거부한다`() {
        val ticket = signer.issue(ConsentSubject.Guest("a796ba5f"))
        val later =
            ConsentTicketSigner(
                properties,
                Clock.fixed(fixedInstant.plusSeconds(ConsentTicketSigner.TTL_SECONDS + 1), ZoneOffset.UTC),
            )

        assertNull(later.verify(ticket))
    }

    @Test
    fun `형식이 깨진 티켓은 거부한다`() {
        assertNull(signer.verify(""))
        assertNull(signer.verify("no-dot"))
        assertNull(signer.verify(".onlysig"))
    }

    @Test
    fun `식별자에 콜론이 섞여도 그대로 보존된다`() {
        val ticket = signer.issue(ConsentSubject.Guest("ab:cd:ef"))

        assertEquals(ConsentSubject.Guest("ab:cd:ef"), signer.verify(ticket))
    }
}
