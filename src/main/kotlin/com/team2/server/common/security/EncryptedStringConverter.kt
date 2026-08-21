package com.team2.server.common.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

/**
 * 컬럼 단위 암복호화.
 *
 * Spring Boot 가 Hibernate 에 `SpringBeanContainer` 를 물려주므로 `@Component` 인 컨버터도
 * 생성자 주입을 받는다. `autoApply` 를 켜지 않는 이유는 모든 String 컬럼이 아니라
 * `@Convert` 를 명시한 컬럼만 암호화하기 위해서다.
 */
@Component
@Converter
class EncryptedStringConverter(
    private val encryptor: AesGcmTokenEncryptor,
) : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let { encryptor.encrypt(it) }

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.let { encryptor.decrypt(it) }
}
