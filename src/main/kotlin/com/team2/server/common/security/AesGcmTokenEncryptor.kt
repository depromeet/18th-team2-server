package com.team2.server.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH = 12
private const val TAG_LENGTH_BITS = 128

/**
 * 저장용 자격증명을 AES-GCM 으로 암복호화한다.
 *
 * 암호문 포맷은 `키 식별자 1바이트 || IV 12바이트 || 암호문+태그` 를 base64 로 인코딩한 것이다.
 * 키 식별자를 지금 넣어두는 이유는 키 회전 기능을 만들기 위해서가 아니라, 나중에 회전이 필요해졌을 때
 * 저장된 데이터를 다시 암호화하지 않고도 새 키를 추가할 수 있게 포맷을 열어두기 위해서다.
 *
 * GCM 은 매번 랜덤 IV 를 쓰므로 같은 원문도 다른 암호문이 된다. 따라서 암호문으로 검색할 수 없다.
 */
@Component
class AesGcmTokenEncryptor(
    @Value("\${app.crypto.token-secret}") secret: String,
) {
    private val key = SecretKeySpec(Base64.getDecoder().decode(secret), "AES")
    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(byteArrayOf(CURRENT_KEY_ID) + iv + encrypted)
    }

    fun decrypt(cipherText: String): String {
        val decoded = decodeCipherText(cipherText)
        if (decoded.isEmpty() || decoded[0] != CURRENT_KEY_ID) {
            throw IllegalStateException("알 수 없는 키 식별자입니다")
        }
        val iv = decoded.copyOfRange(1, 1 + IV_LENGTH)
        val body = decoded.copyOfRange(1 + IV_LENGTH, decoded.size)
        return decryptBody(iv, body)
    }

    private fun decodeCipherText(cipherText: String): ByteArray =
        runCatching { Base64.getDecoder().decode(cipherText) }
            .getOrElse { throw IllegalStateException("암호문 형식이 올바르지 않습니다") }

    private fun decryptBody(
        iv: ByteArray,
        body: ByteArray,
    ): String {
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        return runCatching { String(cipher.doFinal(body), Charsets.UTF_8) }
            .getOrElse { throw IllegalStateException("복호화에 실패했습니다") }
    }

    companion object {
        const val CURRENT_KEY_ID: Byte = 1
    }
}
