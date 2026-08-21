package com.team2.server.support

import com.team2.server.common.security.AesGcmTokenEncryptor
import com.team2.server.common.security.EncryptedStringConverter
import com.team2.server.config.TestcontainersConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

/**
 * `@DataJpaTest` 슬라이스는 `@Converter` 는 포함하지만 그 생성자 의존인 `@Component` 는 제외한다.
 * 암호화 컨버터가 영속성 계층 계약의 일부가 되었으므로 그 의존을 슬라이스에 포함시킨다.
 * 테스트 클래스마다 `@Import` 를 붙이는 대신 여기서 한 번만 처리해 컨텍스트 fingerprint 를 하나로 유지한다.
 */
@DataJpaTest
@Import(
    TestcontainersConfiguration::class,
    AesGcmTokenEncryptor::class,
    EncryptedStringConverter::class,
)
abstract class JpaSliceTestSupport
