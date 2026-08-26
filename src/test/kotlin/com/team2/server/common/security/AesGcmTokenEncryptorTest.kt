package com.team2.server.common.security

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AesGcmTokenEncryptorTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val encryptor = AesGcmTokenEncryptor(secret)

    @Test
    fun `키가 32바이트가 아니면 생성 단계에서 거부한다`() {
        // SecretKeySpec 은 16·24·32바이트를 모두 받아들여 24바이트면 예외 없이 AES-192 로 동작한다.
        // 스펙이 요구하는 AES-256 이 아닌 채로 조용히 넘어가는 것을 막는다.
        val twentyFourBytes = Base64.getEncoder().encodeToString(ByteArray(24))
        val thirtyThreeBytes = Base64.getEncoder().encodeToString(ByteArray(33))

        for (wrong in listOf(twentyFourBytes, thirtyThreeBytes)) {
            val exception = assertFailsWith<IllegalStateException> { AesGcmTokenEncryptor(wrong) }
            assertTrue(exception.message!!.contains("app.crypto.token-secret"), exception.message!!)
        }
    }

    @Test
    fun `키가 Base64 가 아니면 생성 단계에서 거부한다`() {
        val exception = assertFailsWith<IllegalStateException> { AesGcmTokenEncryptor("not!base64!!") }

        assertTrue(exception.message!!.contains("Base64"), exception.message!!)
    }

    @Test
    fun `암호화한 값을 다시 복호화하면 원문이 나온다`() {
        val plain = "kakao-access-token-1234567890"

        assertEquals(plain, encryptor.decrypt(encryptor.encrypt(plain)))
    }

    @Test
    fun `같은 원문도 매번 다른 암호문이 된다`() {
        val plain = "same-token"

        assertNotEquals(encryptor.encrypt(plain), encryptor.encrypt(plain))
    }

    @Test
    fun `암호문 첫 바이트는 키 식별자다`() {
        val decoded = Base64.getDecoder().decode(encryptor.encrypt("token"))

        assertEquals(AesGcmTokenEncryptor.CURRENT_KEY_ID, decoded[0])
    }

    @Test
    fun `한글과 긴 문자열도 왕복한다`() {
        val plain = "한글 토큰 " + "x".repeat(500)

        assertEquals(plain, encryptor.decrypt(encryptor.encrypt(plain)))
    }

    @Test
    fun `다른 키로 복호화하면 실패한다`() {
        val other = AesGcmTokenEncryptor(Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() }))
        val cipherText = encryptor.encrypt("token")

        assertFailsWith<IllegalStateException> { other.decrypt(cipherText) }
    }

    @Test
    fun `암호문이 변조되면 복호화가 실패한다`() {
        val cipherText = encryptor.encrypt("token")
        val bytes = Base64.getDecoder().decode(cipherText)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        val tampered = Base64.getEncoder().encodeToString(bytes)

        assertFailsWith<IllegalStateException> { encryptor.decrypt(tampered) }
    }

    @Test
    fun `모르는 키 식별자면 복호화를 거부한다`() {
        val bytes = Base64.getDecoder().decode(encryptor.encrypt("token"))
        bytes[0] = 0x7F
        val unknown = Base64.getEncoder().encodeToString(bytes)

        val exception = assertFailsWith<IllegalStateException> { encryptor.decrypt(unknown) }

        assertTrue(exception.message!!.contains("키 식별자"), exception.message!!)
    }

    @Test
    fun `잘린 암호문은 진단 가능한 예외로 거부한다`() {
        val truncated = Base64.getEncoder().encodeToString(byteArrayOf(AesGcmTokenEncryptor.CURRENT_KEY_ID, 2, 3))

        val exception = assertFailsWith<IllegalStateException> { encryptor.decrypt(truncated) }

        assertTrue(exception.message!!.contains("잘렸"), exception.message!!)
    }
}
