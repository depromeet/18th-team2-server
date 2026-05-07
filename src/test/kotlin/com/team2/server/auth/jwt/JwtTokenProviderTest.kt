package com.team2.server.auth.jwt

import com.team2.server.auth.config.JwtProperties
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.security.SignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.assertEquals

class JwtTokenProviderTest {
    private val secret: String = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val otherSecret: String = Base64.getEncoder().encodeToString(ByteArray(64) { (it + 1).toByte() })

    private fun newProvider(expirationHours: Long = 24): JwtTokenProvider =
        JwtTokenProvider(JwtProperties(secret = secret, expirationHours = expirationHours))

    private fun newUser(id: Long = 42L): User {
        val user =
            User(
                name = "닉",
                birthDay = "01-01",
                provider = AuthProvider.KAKAO,
                providerId = "kakao-1",
                email = "u@kakao.local",
            )
        // BaseEntity.id 는 val 이지만 테스트용으로 reflection 으로 주입
        val idField: Field = user.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
        user.createdAt = LocalDateTime.now()
        user.updatedAt = LocalDateTime.now()
        return user
    }

    @Test
    fun `issue 후 parse 하면 같은 sub email provider 회수`() {
        val provider = newProvider()
        val user = newUser()

        val token = provider.issue(user)
        val claims = provider.parse(token)

        assertEquals("42", claims.subject)
        assertEquals("u@kakao.local", claims["email"])
        assertEquals("KAKAO", claims["provider"])
    }

    @Test
    fun `만료된 토큰은 ExpiredJwtException`() {
        val provider = JwtTokenProvider(JwtProperties(secret = secret, expirationHours = 0))
        val user = newUser()
        // expirationHours=0 이면 발급 즉시 만료
        val token = provider.issue(user)
        Thread.sleep(50)

        assertThrows<ExpiredJwtException> {
            provider.parse(token)
        }
    }

    @Test
    fun `다른 시크릿으로 검증하면 SignatureException`() {
        val issuer = newProvider()
        val verifier = JwtTokenProvider(JwtProperties(secret = otherSecret, expirationHours = 24))
        val token = issuer.issue(newUser())

        assertThrows<SignatureException> {
            verifier.parse(token)
        }
    }
}
