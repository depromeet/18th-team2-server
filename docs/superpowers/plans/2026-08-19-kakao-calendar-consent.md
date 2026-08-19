# 카카오 톡캘린더 추가 동의 및 토큰 저장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버가 카카오 톡캘린더 추가 동의를 직접 받고 토큰을 암호화 저장해, 클라이언트가 카카오 자격증명을 다루지 않고도 파티 일정을 등록할 수 있게 한다.

**Architecture:** 동의 전용 컨트롤러가 인가 URL 조립·콜백·토큰 교환을 직접 처리한다(Spring Security 로그인 파이프라인은 건드리지 않는다). 받은 토큰은 AES-GCM 으로 컬럼 암호화해 사용자당 한 행으로 저장하고, 만료 시 리프레시로 갱신한다. 등록 엔드포인트는 헤더 대신 저장된 토큰을 쓰며, 토큰 확보와 일정 등록은 서로 다른 트랜잭션에서 일어난다.

**Tech Stack:** Kotlin 2.3, Spring Boot 4.0.5 (webmvc, data-jpa, security), Jackson 3 (`tools.jackson.databind.ObjectMapper`), Flyway, JUnit5 + mockito-kotlin + MockRestServiceServer + Testcontainers(MySQL)

**Spec:** `docs/superpowers/specs/2026-08-18-kakao-talk-calendar-design.md` (2026-08-19 개정)

## Global Constraints

- 커밋 메시지는 `<type>: <한국어 명사형 설명>`, 50자 이내, 마침표 없음. scope 없음. 영문 메시지 금지.
- `git add -A` / `git add .` 금지 — 파일을 개별 지정한다.
- `--no-verify` 금지. `main` / `develop` 직접 커밋 금지 (작업 브랜치: `feature/kakao-calendar-consent`).
- `@Transactional` 은 UseCase 클래스에만 선언한다. Service 에는 금지.
- Service 는 다른 Service 를 호출하지 않는다. UseCase 는 60줄 이내, 생성자 의존성 5개 이내. Service 는 150줄 이내, 의존성 4개 이내, public 메서드 5개 이내.
- UseCase 는 Repository 쓰기(save/delete)를 직접 호출하지 않는다 → Service 에 위임.
- Domain 은 application/api/infrastructure 와 Spring Data 에 의존 금지 (ArchUnit `LayerDependencyTest` 활성).
- `calendar.domain` 은 `party` 패키지를 import 하지 않는다.
- 카카오 동의항목은 **`talk_calendar`** 다. `talk_calendar_task` 는 할 일(task) 전용이므로 쓰지 않는다.
- 카카오 에러 규격: `-401` → HTTP 401(토큰 오류), `-402` → HTTP 403(scope 부족).
- 테스트는 `docs/testing-rules.md` 를 따른다. `@MockitoBean` / `@TestPropertySource` / `@ActiveProfiles` 금지. MockMvc 통합 테스트는 `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration::class)` 조합.
- 새 마이그레이션 번호는 `V15` (develop 최신 `V14`).
- 검증 명령: `./gradlew test --tests "<FQCN>"`, 전체는 `./gradlew build` (ktlint·detekt·ArchUnit 포함).

## 스펙에서 벗어나는 결정 하나

스펙은 403 응답의 `data` 에 `consentUrl` 을 싣는다고 적었으나, 공통 `ErrorResponse` 는
`{status, error:{code, message}}` 구조라 필드를 추가하면 **모든 에러 응답의 형태가 바뀐다.**

대신 클라이언트는 403 `KAKAO_CALENDAR_CONSENT_REQUIRED` 를 받으면
`GET /api/v1/me/talk-calendar-connection/consent-url` 을 호출해 동의 URL 을 받는다.
왕복이 한 번 늘지만 사용자당 한 번뿐이고, 공통 에러 계약을 건드리지 않으며 동의 URL 을 만드는 경로가
하나로 유지된다.

## 트랜잭션 경계

스펙이 팀 결정으로 남긴 부분을 다음과 같이 정한다.

컨트롤러가 UseCase 둘을 순서대로 호출한다. `ResolveKakaoCalendarAccessTokenUseCase` 가 자체 트랜잭션에서
토큰을 확보하고, 그 결과를 기존 `RegisterPartyTalkCalendarEventUseCase` 에 넘긴다.

이 형태를 고른 이유는 셋이다. `@Transactional` 이 UseCase 밖으로 나가지 않아 팀 규칙을 지킨다.
같은 빈 안에서 트랜잭션 메서드를 자기호출하는 프록시 함정이 없다. 그리고 기존 등록 UseCase 가 이미
`kakaoAccessToken` 을 커맨드로 받고 있어 **검증된 코드를 거의 건드리지 않는다** — 토큰의 출처만 바뀐다.

토큰 확보 UseCase 는 예외를 던지지 않고 `String?` 을 돌려준다. `null` 이면 동의가 필요하다는 뜻이다.
예외로 알리면 같은 트랜잭션이 롤백되면서 "죽은 연동을 지운다" 가 함께 되감겨, 매 요청마다 헛된 갱신
요청을 카카오로 보내게 된다.

## 파일 구조

| 파일 | 책임 |
|---|---|
| `common/security/AesGcmTokenEncryptor.kt` | AES-GCM 암복호화. 키 식별자 1바이트 + IV 를 포함한 포맷 |
| `common/security/EncryptedStringConverter.kt` | JPA `AttributeConverter`. 컬럼 단위 투명 암복호화 |
| `calendar/domain/entity/KakaoCalendarConnection.kt` | 연동 엔티티. 토큰과 만료 시각, 갱신 행위 |
| `calendar/infrastructure/persistence/KakaoCalendarConnectionRepository.kt` | 연동 조회. 잠금 조회 포함 |
| `calendar/application/service/KakaoCalendarConnectionService.kt` | 연동 aggregate 저장·조회·삭제 |
| `calendar/application/port/KakaoOAuthPort.kt` | 인가 코드 교환과 리프레시 인터페이스 |
| `calendar/infrastructure/kakao/KakaoOAuthAdapter.kt` | `kauth.kakao.com` 호출 |
| `calendar/infrastructure/kakao/KakaoOAuthConfig.kt` | 인증 서버용 `RestClient` 빈 |
| `calendar/application/service/ConsentTicketSigner.kt` | 티켓 서명·검증. 5분 만료 |
| `calendar/application/service/KakaoConsentUrlFactory.kt` | 동의 진입 URL 과 카카오 인가 URL 조립 |
| `calendar/application/usecase/IssueKakaoCalendarConsentUrlUseCase.kt` | 티켓 발급 + URL 반환 |
| `calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCase.kt` | 토큰 확보 트랜잭션 |
| `calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt` | 콜백에서 연동 저장 |
| `calendar/application/usecase/DisconnectKakaoCalendarUseCase.kt` | 연동 해제 |
| `calendar/api/KakaoCalendarConsentController.kt` | 동의 진입·콜백 |
| `calendar/api/KakaoCalendarConnectionController.kt` | 동의 URL 발급·연동 해제 |
| `calendar/api/KakaoCalendarConsentCookies.kt` | 티켓·복귀 주소 쿠키 |

---

### Task 1: 토큰 암호화 인프라

저장할 자격증명을 컬럼 단위로 암호화한다. 암호문 앞에 키 식별자 1바이트를 붙이는 것은 키 회전 기능을
만드는 것이 아니라 **포맷을 정해두는 것**이다. 지금 넣으면 한 줄이고, 나중에 넣으려면 저장된 데이터를
전부 다시 암호화하는 배치가 필요하다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/common/security/AesGcmTokenEncryptor.kt`
- Create: `src/main/kotlin/com/team2/server/common/security/EncryptedStringConverter.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Test: `src/test/kotlin/com/team2/server/common/security/AesGcmTokenEncryptorTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `AesGcmTokenEncryptor(secret: String)` — `fun encrypt(plainText: String): String`, `fun decrypt(cipherText: String): String`
  - `EncryptedStringConverter` — `AttributeConverter<String, String>`, Spring 빈
  - 설정 키 `app.crypto.token-secret`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/common/security/AesGcmTokenEncryptorTest.kt`

```kotlin
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
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.common.security.AesGcmTokenEncryptorTest"`
Expected: 컴파일 실패 — `AesGcmTokenEncryptor` unresolved reference

- [ ] **Step 3: 암호화 컴포넌트를 작성한다**

`src/main/kotlin/com/team2/server/common/security/AesGcmTokenEncryptor.kt`

```kotlin
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
        val decoded =
            runCatching { Base64.getDecoder().decode(cipherText) }
                .getOrElse { throw IllegalStateException("암호문 형식이 올바르지 않습니다") }
        if (decoded.isEmpty() || decoded[0] != CURRENT_KEY_ID) {
            throw IllegalStateException("알 수 없는 키 식별자입니다")
        }
        val iv = decoded.copyOfRange(1, 1 + IV_LENGTH)
        val body = decoded.copyOfRange(1 + IV_LENGTH, decoded.size)
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
```

- [ ] **Step 4: JPA 컨버터를 작성한다**

`src/main/kotlin/com/team2/server/common/security/EncryptedStringConverter.kt`

```kotlin
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
```

- [ ] **Step 5: 설정을 추가한다**

`src/main/resources/application.yml` 의 `app:` 블록에 `crypto` 를 더한다.

```yaml
app:
  web-base-url: ${APP_WEB_BASE_URL:http://localhost:5173}
```

`app.crypto.token-secret` 은 base yml 에 두지 않는다. 시크릿 서브모듈의 `application-secret*.yml` 이
직접 정의하므로 base 의 플레이스홀더는 평가되지 않고, 없을 때의 에러 메시지도 실제 프로퍼티 이름을 짚어준다.

`src/test/resources/application.yml` 의 `app:` 블록에도 고정 키를 추가한다.

```yaml
  crypto:
    token-secret: dGVzdC1jcnlwdG8ta2V5LTMyYnl0ZXMtYWVzLWdjbSE=
```

- [ ] **Step 6: 테스트 키가 32바이트인지 확인한다**

Run: `echo -n 'dGVzdC1jcnlwdG8ta2V5LTMyYnl0ZXMtYWVzLWdjbSE=' | base64 -d | wc -c`
Expected: `32`. 다른 값이 나오면 진행하지 말고 보고한다 — AES-256 은 정확히 32바이트를 요구한다.

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.common.security.AesGcmTokenEncryptorTest"`
Expected: PASS (7 tests)

- [ ] **Step 8: Spring 컨텍스트가 뜨는지 확인한다**

`AesGcmTokenEncryptor` 는 `@Component` 라 컨텍스트 기동 시 생성된다. 키가 비었거나 32바이트가 아니면
여기서 깨진다.

Run: `./gradlew test --tests "com.team2.server.ServerApplicationTests"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/security/AesGcmTokenEncryptor.kt \
        src/main/kotlin/com/team2/server/common/security/EncryptedStringConverter.kt \
        src/main/resources/application.yml \
        src/test/resources/application.yml \
        src/test/kotlin/com/team2/server/common/security/AesGcmTokenEncryptorTest.kt
git commit -m "feat: 저장용 토큰 암호화 컴포넌트 추가"
```

---

### Task 2: 연동 엔티티와 저장소

사용자당 한 행으로 카카오 연동을 저장한다. 토큰 컬럼은 Task 1 의 컨버터로 암호화된다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/domain/entity/KakaoCalendarConnection.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/KakaoCalendarConnectionRepository.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionService.kt`
- Create: `src/main/resources/db/migration/V15__create_kakao_calendar_connection.sql`
- Test: `src/test/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionServiceTest.kt`

**Interfaces:**
- Consumes: `EncryptedStringConverter` (Task 1), `BaseEntity`
- Produces:
  - `KakaoCalendarConnection(userId: Long, accessToken: String, refreshToken: String, accessTokenExpiresAt: LocalDateTime, refreshTokenExpiresAt: LocalDateTime)`
    — `val userId`, `var accessToken` (private set), `var refreshToken` (private set), `var accessTokenExpiresAt` (private set), `var refreshTokenExpiresAt` (private set)
    — `fun isAccessTokenUsableAt(now: LocalDateTime): Boolean`, `fun isRefreshTokenExpiredAt(now: LocalDateTime): Boolean`
    — `fun applyRefreshed(accessToken: String, accessTokenExpiresAt: LocalDateTime, refreshToken: String?, refreshTokenExpiresAt: LocalDateTime?)`
    — `companion object { const val ACCESS_TOKEN_LEEWAY_SECONDS = 60L; const val UK_USER = "uk_kakao_calendar_connection_user" }`
  - `KakaoCalendarConnectionRepository` — `fun findByUserId(userId: Long): KakaoCalendarConnection?` (`PESSIMISTIC_WRITE`)
  - `KakaoCalendarConnectionService` — `fun find(userId: Long): KakaoCalendarConnection?`, `fun save(connection: KakaoCalendarConnection): KakaoCalendarConnection`, `fun delete(connection: KakaoCalendarConnection)`

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionServiceTest.kt`

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.support.JpaSliceTestSupport
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoCalendarConnectionServiceTest
    @Autowired
    constructor(
        private val repository: KakaoCalendarConnectionRepository,
        private val entityManager: EntityManager,
    ) : JpaSliceTestSupport() {
        private val service = KakaoCalendarConnectionService(repository)
        private val now = LocalDateTime.of(2026, 8, 19, 12, 0)

        private fun connection(
            userId: Long = 1L,
            accessExpiresAt: LocalDateTime = now.plusHours(6),
            refreshExpiresAt: LocalDateTime = now.plusMonths(2),
        ) = KakaoCalendarConnection(
            userId = userId,
            accessToken = "access-token-value",
            refreshToken = "refresh-token-value",
            accessTokenExpiresAt = accessExpiresAt,
            refreshTokenExpiresAt = refreshExpiresAt,
        )

        @Test
        fun `연동이 없으면 null 을 반환한다`() {
            assertNull(service.find(userId = 1L))
        }

        @Test
        fun `저장한 연동을 사용자로 조회한다`() {
            service.save(connection())

            val found = service.find(userId = 1L)

            assertEquals("access-token-value", found?.accessToken)
            assertEquals("refresh-token-value", found?.refreshToken)
        }

        @Test
        fun `토큰은 DB 에 평문으로 저장되지 않는다`() {
            service.save(connection())
            entityManager.flush()
            entityManager.clear()

            val stored =
                entityManager
                    .createNativeQuery("select access_token, refresh_token from kakao_calendar_connection")
                    .singleResult as Array<*>

            assertFalse((stored[0] as String).contains("access-token-value"))
            assertFalse((stored[1] as String).contains("refresh-token-value"))
        }

        @Test
        fun `연동을 삭제한다`() {
            service.save(connection())

            service.delete(service.find(userId = 1L)!!)

            assertNull(service.find(userId = 1L))
        }

        @Test
        fun `액세스 토큰 만료가 60초 이상 남았으면 사용 가능하다`() {
            val target = connection(accessExpiresAt = now.plusSeconds(61))

            assertTrue(target.isAccessTokenUsableAt(now))
        }

        @Test
        fun `액세스 토큰 만료가 60초 이하로 남았으면 사용 불가다`() {
            val target = connection(accessExpiresAt = now.plusSeconds(60))

            assertFalse(target.isAccessTokenUsableAt(now))
        }

        @Test
        fun `리프레시 토큰 만료 시각이 지났으면 만료로 본다`() {
            val target = connection(refreshExpiresAt = now)

            assertTrue(target.isRefreshTokenExpiredAt(now))
        }

        @Test
        fun `갱신은 새 액세스 토큰을 반영하고 리프레시 토큰이 없으면 기존 것을 유지한다`() {
            val target = connection()

            target.applyRefreshed(
                accessToken = "new-access",
                accessTokenExpiresAt = now.plusHours(6),
                refreshToken = null,
                refreshTokenExpiresAt = null,
            )

            assertEquals("new-access", target.accessToken)
            assertEquals("refresh-token-value", target.refreshToken)
        }

        @Test
        fun `갱신 응답에 리프레시 토큰이 있으면 교체한다`() {
            val target = connection()

            target.applyRefreshed(
                accessToken = "new-access",
                accessTokenExpiresAt = now.plusHours(6),
                refreshToken = "new-refresh",
                refreshTokenExpiresAt = now.plusMonths(2),
            )

            assertEquals("new-refresh", target.refreshToken)
        }
    }
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.KakaoCalendarConnectionServiceTest"`
Expected: 컴파일 실패 — `KakaoCalendarConnection` 외 unresolved reference

- [ ] **Step 3: 엔티티를 작성한다**

`src/main/kotlin/com/team2/server/calendar/domain/entity/KakaoCalendarConnection.kt`

```kotlin
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
```

- [ ] **Step 4: 리포지토리를 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/KakaoCalendarConnectionRepository.kt`

```kotlin
package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface KakaoCalendarConnectionRepository : JpaRepository<KakaoCalendarConnection, Long> {
    /**
     * 연동 행을 잠그고 읽는다.
     *
     * 같은 사용자의 요청 둘이 동시에 갱신하면 카카오에 갱신 요청이 두 번 나가고, 카카오가 새 리프레시
     * 토큰을 발급하며 기존 것을 폐기하면 한쪽이 무효한 토큰을 저장해 연동이 깨진다. 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserId(userId: Long): KakaoCalendarConnection?
}
```

- [ ] **Step 5: 서비스를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionService.kt`

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import org.springframework.stereotype.Service

@Service
class KakaoCalendarConnectionService(
    private val kakaoCalendarConnectionRepository: KakaoCalendarConnectionRepository,
) {
    fun find(userId: Long): KakaoCalendarConnection? = kakaoCalendarConnectionRepository.findByUserId(userId)

    fun save(connection: KakaoCalendarConnection): KakaoCalendarConnection =
        kakaoCalendarConnectionRepository.save(connection)

    fun delete(connection: KakaoCalendarConnection) {
        kakaoCalendarConnectionRepository.delete(connection)
        kakaoCalendarConnectionRepository.flush()
    }
}
```

- [ ] **Step 6: 마이그레이션을 작성한다**

`src/main/resources/db/migration/V15__create_kakao_calendar_connection.sql`

```sql
create table kakao_calendar_connection (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    access_token varchar(1024) not null,
    refresh_token varchar(1024) not null,
    access_token_expires_at datetime(6) not null,
    refresh_token_expires_at datetime(6) not null,
    primary key (id),
    constraint uk_kakao_calendar_connection_user unique (user_id)
);
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.KakaoCalendarConnectionServiceTest"`
Expected: PASS (9 tests)

`토큰은 DB 에 평문으로 저장되지 않는다` 가 실패하면 컨버터가 Spring 주입을 못 받은 것이다.
`EncryptedStringConverter` 에 `@Component` 가 붙어 있는지 확인한다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/domain/entity/KakaoCalendarConnection.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/persistence/KakaoCalendarConnectionRepository.kt \
        src/main/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionService.kt \
        src/main/resources/db/migration/V15__create_kakao_calendar_connection.sql \
        src/test/kotlin/com/team2/server/calendar/application/service/KakaoCalendarConnectionServiceTest.kt
git commit -m "feat: 카카오 캘린더 연동 엔티티와 저장소 추가"
```

---

### Task 3: 카카오 인증 서버 어댑터

인가 코드를 토큰으로 바꾸고, 리프레시로 갱신한다. 호스트가 `kauth.kakao.com` 이라 일정 API 어댑터와
별개의 `RestClient` 를 쓴다. client-id 와 client-secret 은 로그인이 쓰는 값을
`ClientRegistrationRepository` 에서 읽어 재사용한다 — 같은 앱의 같은 자격증명을 두 벌로 관리하지 않는다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/port/KakaoOAuthPort.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthConfig.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapter.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Test: `src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapterTest.kt`

**Interfaces:**
- Consumes: `BusinessException` / `ErrorCode`
- Produces:
  - `data class KakaoOAuthTokens(val accessToken: String, val accessTokenExpiresInSeconds: Long, val refreshToken: String?, val refreshTokenExpiresInSeconds: Long?)`
  - `interface KakaoOAuthPort` — `fun exchange(code: String, redirectUri: String): KakaoOAuthTokens?`, `fun refresh(refreshToken: String): KakaoOAuthTokens?`
    (둘 다 `null` 은 "카카오가 거부했다", 예외는 "카카오에 닿지 못했거나 장애다")
  - Bean 이름 `kakaoOAuthRestClient`, 설정 키 `kakao.auth.base-url`

- [ ] **Step 1: 실패하는 어댑터 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapterTest.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod as SpringHttpMethod

class KakaoOAuthAdapterTest {
    private val builder = RestClient.builder().baseUrl("https://kauth.kakao.com")
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val adapter =
        KakaoOAuthAdapter(
            restClient = builder.build(),
            objectMapper = ObjectMapper(),
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
        )

    @Test
    fun `인가 코드를 토큰으로 교환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andExpect(content().string(containsString("code=auth-code")))
            .andExpect(content().string(containsString("client_id=test-client-id")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "access_token": "access-1",
                      "expires_in": 21599,
                      "refresh_token": "refresh-1",
                      "refresh_token_expires_in": 5183999
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tokens = adapter.exchange("auth-code", "https://api.example.com/callback")

        assertEquals("access-1", tokens?.accessToken)
        assertEquals(21599L, tokens?.accessTokenExpiresInSeconds)
        assertEquals("refresh-1", tokens?.refreshToken)
        assertEquals(5183999L, tokens?.refreshTokenExpiresInSeconds)
        server.verify()
    }

    @Test
    fun `리프레시로 갱신한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(content().string(containsString("grant_type=refresh_token")))
            .andExpect(content().string(containsString("refresh_token=refresh-1")))
            .andRespond(
                withSuccess(
                    """{"access_token":"access-2","expires_in":21599}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tokens = adapter.refresh("refresh-1")

        assertEquals("access-2", tokens?.accessToken)
        assertNull(tokens?.refreshToken)
        assertNull(tokens?.refreshTokenExpiresInSeconds)
    }

    @Test
    fun `카카오가 거부하면 null 을 반환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"invalid_grant","error_description":"expired refresh token"}"""),
            )

        assertNull(adapter.refresh("refresh-1"))
    }

    @Test
    fun `카카오 장애면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `연결 실패도 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond { throw java.io.IOException("connect timed out") }

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `응답에 access_token 이 없으면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withSuccess("""{"expires_in":21599}""", MediaType.APPLICATION_JSON))

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.kakao.KakaoOAuthAdapterTest"`
Expected: 컴파일 실패 — `KakaoOAuthAdapter` unresolved reference

- [ ] **Step 3: Port 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/port/KakaoOAuthPort.kt`

```kotlin
package com.team2.server.calendar.application.port

/**
 * 카카오 인증 서버(`kauth.kakao.com`) 와의 토큰 교환.
 *
 * 반환값이 `null` 이면 카카오가 요청을 거부한 것이다(만료된 리프레시 토큰, 철회된 동의 등).
 * 이 경우 재시도해도 소용없으므로 호출자는 연동을 정리하고 다시 동의를 받아야 한다.
 * 카카오에 닿지 못했거나 장애인 경우에는 예외를 던진다 — 그건 나중에 다시 시도할 수 있는 상황이다.
 */
interface KakaoOAuthPort {
    fun exchange(
        code: String,
        redirectUri: String,
    ): KakaoOAuthTokens?

    fun refresh(refreshToken: String): KakaoOAuthTokens?
}

data class KakaoOAuthTokens(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String?,
    val refreshTokenExpiresInSeconds: Long?,
)
```

- [ ] **Step 4: RestClient 설정을 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthConfig.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

private const val CONNECT_TIMEOUT_SECONDS = 2L
private const val READ_TIMEOUT_SECONDS = 5L

@Configuration
class KakaoOAuthConfig(
    @Value("\${kakao.auth.base-url:https://kauth.kakao.com}")
    private val baseUrl: String,
) {
    /** 토큰 확보 트랜잭션 안에서 호출되므로 일정 API 와 같은 타임아웃을 건다. */
    @Bean
    fun kakaoOAuthRestClient(): RestClient {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
            }
        return RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
```

- [ ] **Step 5: 어댑터를 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapter.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

private const val TOKEN_PATH = "/oauth/token"
private const val ERROR_BODY_LOG_MAX_LENGTH = 500

@Component
class KakaoOAuthAdapter(
    @Qualifier("kakaoOAuthRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val clientId: String,
    private val clientSecret: String,
) : KakaoOAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인이 쓰는 카카오 자격증명을 그대로 재사용한다. */
    @Autowired
    constructor(
        @Qualifier("kakaoOAuthRestClient") restClient: RestClient,
        objectMapper: ObjectMapper,
        clientRegistrationRepository: ClientRegistrationRepository,
    ) : this(
        restClient = restClient,
        objectMapper = objectMapper,
        clientId = clientRegistrationRepository.findByRegistrationId("kakao").clientId,
        clientSecret = clientRegistrationRepository.findByRegistrationId("kakao").clientSecret,
    )

    override fun exchange(
        code: String,
        redirectUri: String,
    ): KakaoOAuthTokens? {
        val form =
            baseForm("authorization_code").apply {
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        return requestTokens(form)
    }

    override fun refresh(refreshToken: String): KakaoOAuthTokens? {
        val form =
            baseForm("refresh_token").apply {
                add("refresh_token", refreshToken)
            }
        return requestTokens(form)
    }

    private fun baseForm(grantType: String): MultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("grant_type", grantType)
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }

    private fun requestTokens(form: MultiValueMap<String, String>): KakaoOAuthTokens? {
        val response = post(form)
        if (response.statusCode.is4xxClientError) {
            log.warn(
                "카카오 토큰 요청 거부. status={}, body={}",
                response.statusCode.value(),
                response.body?.take(ERROR_BODY_LOG_MAX_LENGTH),
            )
            return null
        }
        if (!response.statusCode.is2xxSuccessful) {
            log.warn(
                "카카오 토큰 요청 실패. status={}, body={}",
                response.statusCode.value(),
                response.body?.take(ERROR_BODY_LOG_MAX_LENGTH),
            )
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }
        return parse(response.body)
    }

    private fun post(form: MultiValueMap<String, String>): ResponseEntity<String> =
        try {
            restClient
                .post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange { _, response ->
                    ResponseEntity
                        .status(response.statusCode)
                        .body(response.bodyTo(String::class.java))
                }
        } catch (e: RestClientException) {
            log.warn("카카오 인증 서버 호출 실패", e)
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun parse(body: String?): KakaoOAuthTokens {
        val parsed =
            runCatching { objectMapper.readValue(body ?: "", Map::class.java) }
                .getOrElse { throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE) }
        val accessToken =
            parsed["access_token"] as? String
                ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        return KakaoOAuthTokens(
            accessToken = accessToken,
            accessTokenExpiresInSeconds = (parsed["expires_in"] as? Number)?.toLong()
                ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE),
            refreshToken = parsed["refresh_token"] as? String,
            refreshTokenExpiresInSeconds = (parsed["refresh_token_expires_in"] as? Number)?.toLong(),
        )
    }
}
```

- [ ] **Step 6: 설정을 추가한다**

`src/main/resources/application.yml` 의 `kakao:` 블록에 `auth` 를 더한다.

```yaml
kakao:
  talk-calendar:
    base-url: "https://kapi.kakao.com"
  auth:
    base-url: "https://kauth.kakao.com"
```

`src/test/resources/application.yml` 도 같은 자리에 루프백을 가리키게 한다.

```yaml
kakao:
  talk-calendar:
    base-url: "http://localhost:19595"
  auth:
    base-url: "http://localhost:19596"
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.infrastructure.kakao.KakaoOAuthAdapterTest"`
Expected: PASS (6 tests)

- [ ] **Step 8: Spring 컨텍스트가 뜨는지 확인한다**

`@Autowired` 를 붙인 생성자가 선택되므로 `clientId` 를 String 빈으로 찾다가 실패하는 일은 없어야 한다.
그래도 실패하면 주 생성자를 `internal` 로 바꿔 테스트에서만 쓰이게 한다.

Run: `./gradlew test --tests "com.team2.server.ServerApplicationTests"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/port/KakaoOAuthPort.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthConfig.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapter.kt \
        src/main/resources/application.yml \
        src/test/resources/application.yml \
        src/test/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoOAuthAdapterTest.kt
git commit -m "feat: 카카오 인증 서버 어댑터 추가"
```

---

### Task 4: 동의 티켓

동의를 시작한 사용자를 콜백까지 안전하게 나른다. 티켓이 곧 OAuth `state` 값이다.

서명 키로 `app.jwt.secret` 을 재사용한다. 암호화 키를 따로 둔 것과 다른 판단인데, 이유는 용도가 같기
때문이다. JWT 시크릿은 이미 HMAC **서명** 키이고 티켓도 서명이다. 반면 암호화는 다른 용도라
같은 키를 쓰면 JWT 시크릿을 교체하는 순간 저장된 토큰을 복호화할 수 없게 된다. 티켓은 5분짜리라
키가 바뀌어도 잃을 것이 없다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/service/ConsentTicketSigner.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt`

**Interfaces:**
- Consumes: `JwtProperties` (`com.team2.server.auth.config.JwtProperties`, 필드 `secret: String`)
- Produces:
  - `ConsentTicketSigner(jwtProperties: JwtProperties, clock: Clock)` — `fun issue(userId: Long): String`, `fun verify(ticket: String): Long?`
    (`verify` 는 유효하면 `userId`, 위조·만료·형식 오류면 `null`)
  - `companion object { const val TTL_SECONDS = 300L }`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt`

```kotlin
package com.team2.server.calendar.application.service

import com.team2.server.auth.config.JwtProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsentTicketSignerTest {
    private val properties =
        JwtProperties(
            secret = "dGVzdC1zZWNyZXQta2V5LXdpdGgtYXQtbGVhc3QtMjU2LWJpdHMtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobS0xMjM0NTY3OA==",
            expirationHours = 24,
        )
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
        val payload = String(java.util.Base64.getUrlDecoder().decode(ticket.substringBefore('.')))
        val tamperedPayload =
            java.util.Base64
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
        assert(first != second)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.ConsentTicketSignerTest"`
Expected: 컴파일 실패 — `ConsentTicketSigner` unresolved reference

- [ ] **Step 3: 티켓 서명기를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/service/ConsentTicketSigner.kt`

```kotlin
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
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.ConsentTicketSignerTest"`
Expected: PASS (7 tests)

`JwtProperties` 생성자 파라미터 이름이 다르면 `src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt`
를 열어 실제 시그니처에 맞춘다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/service/ConsentTicketSigner.kt \
        src/test/kotlin/com/team2/server/calendar/application/service/ConsentTicketSignerTest.kt
git commit -m "feat: 동의 티켓 서명 검증 추가"
```

---

### Task 5: 토큰 확보 UseCase

등록 요청 시점에 쓸 액세스 토큰을 확보한다. 이 태스크의 핵심 제약은 **예외를 던지지 않는 것**이다.
동의가 필요한 상황을 예외로 알리면 같은 트랜잭션이 롤백되면서 "죽은 연동을 지운다" 가 함께 되감기고,
매 요청마다 헛된 갱신 요청을 카카오로 보내게 된다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCaseTest.kt`

**Interfaces:**
- Consumes: `KakaoCalendarConnectionService` (Task 2), `KakaoOAuthPort` / `KakaoOAuthTokens` (Task 3), `KakaoCalendarConnection` (Task 2)
- Produces: `ResolveKakaoCalendarAccessTokenUseCase` — `operator fun invoke(userId: Long): String?`
  (`null` 이면 동의가 필요하다는 뜻)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCaseTest.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveKakaoCalendarAccessTokenUseCaseTest {
    private val connectionService: KakaoCalendarConnectionService = mock()
    private val kakaoOAuthPort: KakaoOAuthPort = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 19, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase = ResolveKakaoCalendarAccessTokenUseCase(connectionService, kakaoOAuthPort, clock)

    private fun connection(
        accessExpiresAt: LocalDateTime = fixedNow.plusHours(6),
        refreshExpiresAt: LocalDateTime = fixedNow.plusMonths(2),
    ) = KakaoCalendarConnection(
        userId = 10L,
        accessToken = "stored-access",
        refreshToken = "stored-refresh",
        accessTokenExpiresAt = accessExpiresAt,
        refreshTokenExpiresAt = refreshExpiresAt,
    )

    @Test
    fun `연동이 없으면 null 을 반환한다`() {
        whenever(connectionService.find(10L)).thenReturn(null)

        assertNull(useCase(10L))
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `액세스 토큰이 유효하면 그대로 쓴다`() {
        whenever(connectionService.find(10L)).thenReturn(connection())

        assertEquals("stored-access", useCase(10L))
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `액세스 토큰이 만료 임박이면 갱신한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh"))
            .thenReturn(KakaoOAuthTokens("new-access", 21599L, null, null))

        assertEquals("new-access", useCase(10L))
        assertEquals(fixedNow.plusSeconds(21599), target.accessTokenExpiresAt)
        verify(connectionService, never()).delete(any())
    }

    @Test
    fun `갱신 응답에 리프레시 토큰이 오면 함께 반영한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh"))
            .thenReturn(KakaoOAuthTokens("new-access", 21599L, "new-refresh", 5183999L))

        useCase(10L)

        assertEquals("new-refresh", target.refreshToken)
        assertEquals(fixedNow.plusSeconds(5183999), target.refreshTokenExpiresAt)
    }

    @Test
    fun `리프레시 토큰이 만료됐으면 연동을 지우고 null 을 반환한다`() {
        val target = connection(accessExpiresAt = fixedNow, refreshExpiresAt = fixedNow)
        whenever(connectionService.find(10L)).thenReturn(target)

        assertNull(useCase(10L))
        verify(connectionService).delete(target)
        verify(kakaoOAuthPort, never()).refresh(any())
    }

    @Test
    fun `카카오가 갱신을 거부하면 연동을 지우고 null 을 반환한다`() {
        val target = connection(accessExpiresAt = fixedNow.plusSeconds(30))
        whenever(connectionService.find(10L)).thenReturn(target)
        whenever(kakaoOAuthPort.refresh("stored-refresh")).thenReturn(null)

        assertNull(useCase(10L))
        verify(connectionService).delete(target)
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.ResolveKakaoCalendarAccessTokenUseCaseTest"`
Expected: 컴파일 실패 — `ResolveKakaoCalendarAccessTokenUseCase` unresolved reference

- [ ] **Step 3: UseCase 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCase.kt`

```kotlin
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
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.ResolveKakaoCalendarAccessTokenUseCaseTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCase.kt \
        src/test/kotlin/com/team2/server/calendar/application/usecase/ResolveKakaoCalendarAccessTokenUseCaseTest.kt
git commit -m "feat: 카카오 캘린더 토큰 확보 유스케이스 추가"
```

---

### Task 6: 동의 URL 발급과 연동 해제

동의 URL 을 만드는 경로를 하나로 두고, 사용자가 연동을 거둬갈 수단을 제공한다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactory.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/IssueKakaoCalendarConsentUrlUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/DisconnectKakaoCalendarUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/dto/KakaoCalendarConsentUrlResult.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionApi.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionController.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Test: `src/test/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactoryTest.kt`

**Interfaces:**
- Consumes: `ConsentTicketSigner` (Task 4), `KakaoCalendarConnectionService` (Task 2)
- Produces:
  - `KakaoConsentUrlFactory(apiBaseUrl: String, authBaseUrl: String, clientId: String)` — `fun consentEntryUrl(ticket: String, redirectUri: String): String`, `fun kakaoAuthorizeUrl(ticket: String): String`, `fun callbackUri(): String`
  - `data class KakaoCalendarConsentUrlResult(val consentUrl: String)`
  - `IssueKakaoCalendarConsentUrlUseCase` — `operator fun invoke(userId: Long, redirectUri: String): KakaoCalendarConsentUrlResult`
  - `DisconnectKakaoCalendarUseCase` — `operator fun invoke(userId: Long)`
  - 설정 키 `app.api-base-url`
  - 엔드포인트 `GET /api/v1/me/talk-calendar-connection/consent-url?redirectUri=...`, `DELETE /api/v1/me/talk-calendar-connection`

- [ ] **Step 1: 실패하는 URL 조립 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactoryTest.kt`

```kotlin
package com.team2.server.calendar.application.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KakaoConsentUrlFactoryTest {
    private val factory =
        KakaoConsentUrlFactory(
            apiBaseUrl = "https://api.example.com",
            authBaseUrl = "https://kauth.kakao.com",
            clientId = "test-client-id",
        )

    @Test
    fun `동의 진입 URL 에 티켓과 복귀 주소가 인코딩되어 담긴다`() {
        val url = factory.consentEntryUrl("ticket-1", "https://web.example.com/party/1")

        assertTrue(url.startsWith("https://api.example.com/api/v1/kakao-calendar/consent?"), url)
        assertTrue(url.contains("ticket=ticket-1"), url)
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fweb.example.com%2Fparty%2F1"), url)
    }

    @Test
    fun `카카오 인가 URL 은 talk_calendar scope 와 티켓 state 를 쓴다`() {
        val url = factory.kakaoAuthorizeUrl("ticket-1")

        assertTrue(url.startsWith("https://kauth.kakao.com/oauth/authorize?"), url)
        assertTrue(url.contains("client_id=test-client-id"), url)
        assertTrue(url.contains("response_type=code"), url)
        assertTrue(url.contains("scope=talk_calendar"), url)
        assertTrue(url.contains("state=ticket-1"), url)
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapi.example.com%2Fapi%2Fv1%2Fkakao-calendar%2Fconsent%2Fcallback"), url)
    }

    @Test
    fun `scope 는 할 일 권한이 아니라 일정 권한이다`() {
        assertTrue(factory.kakaoAuthorizeUrl("t").contains("scope=talk_calendar&"))
        assertTrue(!factory.kakaoAuthorizeUrl("t").contains("talk_calendar_task"))
    }

    @Test
    fun `콜백 주소는 토큰 교환 때와 같은 값을 쓴다`() {
        assertEquals("https://api.example.com/api/v1/kakao-calendar/consent/callback", factory.callbackUri())
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.KakaoConsentUrlFactoryTest"`
Expected: 컴파일 실패 — `KakaoConsentUrlFactory` unresolved reference

- [ ] **Step 3: URL 조립기를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactory.kt`

```kotlin
package com.team2.server.calendar.application.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder

const val CONSENT_ENTRY_PATH = "/api/v1/kakao-calendar/consent"
const val CONSENT_CALLBACK_PATH = "/api/v1/kakao-calendar/consent/callback"

/** 일정 생성·조회·편집 권한. `talk_calendar_task` 는 할 일 전용이라 쓰지 않는다. */
const val TALK_CALENDAR_SCOPE = "talk_calendar"

@Service
class KakaoConsentUrlFactory(
    private val apiBaseUrl: String,
    private val authBaseUrl: String,
    private val clientId: String,
) {
    @Autowired
    constructor(
        @Value("\${app.api-base-url}") apiBaseUrl: String,
        @Value("\${kakao.auth.base-url:https://kauth.kakao.com}") authBaseUrl: String,
        clientRegistrationRepository: ClientRegistrationRepository,
    ) : this(
        apiBaseUrl = apiBaseUrl,
        authBaseUrl = authBaseUrl,
        clientId = clientRegistrationRepository.findByRegistrationId("kakao").clientId,
    )

    /** 클라이언트가 브라우저를 보낼 주소. 카카오 주소가 아니라 우리 진입점이다. */
    fun consentEntryUrl(
        ticket: String,
        redirectUri: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(apiBaseUrl + CONSENT_ENTRY_PATH)
            .queryParam("ticket", ticket)
            .queryParam("redirect_uri", redirectUri)
            .encode()
            .toUriString()

    fun kakaoAuthorizeUrl(ticket: String): String =
        UriComponentsBuilder
            .fromUriString("$authBaseUrl/oauth/authorize")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", callbackUri())
            .queryParam("response_type", "code")
            .queryParam("scope", TALK_CALENDAR_SCOPE)
            .queryParam("state", ticket)
            .encode()
            .toUriString()

    /** 인가 요청과 토큰 교환에서 반드시 같은 값을 써야 한다. 다르면 카카오가 거부한다. */
    fun callbackUri(): String = apiBaseUrl + CONSENT_CALLBACK_PATH
}
```

- [ ] **Step 4: UseCase 둘과 결과 DTO 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/dto/KakaoCalendarConsentUrlResult.kt`

```kotlin
package com.team2.server.calendar.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "톡캘린더 동의 URL")
data class KakaoCalendarConsentUrlResult(
    @Schema(
        description = "브라우저를 이 주소로 보내면 카카오 동의를 거쳐 복귀 주소로 돌아온다",
        example = "https://api.hapalin.com/api/v1/kakao-calendar/consent?ticket=...&redirect_uri=...",
    )
    val consentUrl: String,
)
```

`src/main/kotlin/com/team2/server/calendar/application/usecase/IssueKakaoCalendarConsentUrlUseCase.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import org.springframework.stereotype.Service

@Service
class IssueKakaoCalendarConsentUrlUseCase(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
) {
    operator fun invoke(
        userId: Long,
        redirectUri: String,
    ): KakaoCalendarConsentUrlResult =
        KakaoCalendarConsentUrlResult(
            consentUrl = kakaoConsentUrlFactory.consentEntryUrl(consentTicketSigner.issue(userId), redirectUri),
        )
}
```

`src/main/kotlin/com/team2/server/calendar/application/usecase/DisconnectKakaoCalendarUseCase.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 저장된 토큰을 지운다.
 *
 * 카카오 쪽 연결 해제(`unlink`)는 하지 않는다. 앱 전체 연결이 끊겨 사용자가 로그인조차 할 수 없게 되므로
 * 캘린더 연동만 끊는다는 의도와 맞지 않는다.
 */
@Service
class DisconnectKakaoCalendarUseCase(
    private val kakaoCalendarConnectionService: KakaoCalendarConnectionService,
) {
    @Transactional
    operator fun invoke(userId: Long) {
        kakaoCalendarConnectionService.find(userId)?.let { kakaoCalendarConnectionService.delete(it) }
    }
}
```

- [ ] **Step 5: Swagger 스펙 인터페이스를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionApi.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Talk Calendar", description = "카카오 톡캘린더 연동 API")
interface KakaoCalendarConnectionApi {
    @Operation(
        summary = "톡캘린더 동의 URL 발급",
        description = """
브라우저를 반환된 `consentUrl` 로 보내면 카카오 동의를 거쳐 `redirectUri` 로 돌아온다.
복귀 시 쿼리 파라미터 `calendarConsent` 에 결과가 담긴다
(`granted` / `denied` / `account_mismatch` / `expired` / `failed`).

일정 등록이 403 `KAKAO_CALENDAR_CONSENT_REQUIRED` 를 반환했을 때 이 엔드포인트를 호출한다.
마이페이지에서 미리 연동하는 흐름에도 같은 엔드포인트를 쓴다.
""",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "발급 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun issueConsentUrl(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "동의 후 돌아올 프론트 주소", example = "https://hapalin.com/mypage")
        redirectUri: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult>

    @Operation(
        summary = "톡캘린더 연동 해제",
        description = "서버에 저장된 카카오 토큰을 지운다. 카카오 계정 전체 연결은 끊지 않는다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "204", description = "해제 완료. 연동이 없어도 204")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun disconnect(
        @Parameter(hidden = true) principal: UserPrincipal,
    )
}
```

- [ ] **Step 6: 컨트롤러를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionController.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.KakaoCalendarConsentUrlResult
import com.team2.server.calendar.application.usecase.DisconnectKakaoCalendarUseCase
import com.team2.server.calendar.application.usecase.IssueKakaoCalendarConsentUrlUseCase
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/talk-calendar-connection")
class KakaoCalendarConnectionController(
    private val issueKakaoCalendarConsentUrlUseCase: IssueKakaoCalendarConsentUrlUseCase,
    private val disconnectKakaoCalendarUseCase: DisconnectKakaoCalendarUseCase,
) : KakaoCalendarConnectionApi {
    @GetMapping("/consent-url")
    override fun issueConsentUrl(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam redirectUri: String,
    ): ApiResponse<KakaoCalendarConsentUrlResult> =
        ApiResponse.success(
            HttpStatus.OK,
            issueKakaoCalendarConsentUrlUseCase(principal.userId, redirectUri),
        )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    override fun disconnect(
        @AuthenticationPrincipal principal: UserPrincipal,
    ) {
        disconnectKakaoCalendarUseCase(principal.userId)
    }
}
```

- [ ] **Step 7: 설정을 추가한다**

`src/main/resources/application.yml` 의 `app:` 블록에 API 자신의 주소를 더한다.
카카오 인가 요청과 토큰 교환에 같은 콜백 주소를 써야 하므로 서버가 자기 주소를 알아야 한다.

```yaml
app:
  api-base-url: ${APP_API_BASE_URL:http://localhost:8080}
```

`src/main/resources/application-dev.yml` 과 `application-prod.yml` 의 `app:` 블록에도 넣는다.

```yaml
  # dev
  api-base-url: ${APP_API_BASE_URL:https://dev-api.hapalin.com}
  # prod
  api-base-url: ${APP_API_BASE_URL:https://api.hapalin.com}
```

`src/test/resources/application.yml` 의 `app:` 블록에도 고정값을 넣는다.

```yaml
  api-base-url: "http://localhost:8080"
```

- [ ] **Step 8: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.service.KakaoConsentUrlFactoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 9: Spring 컨텍스트가 뜨는지 확인한다**

Run: `./gradlew test --tests "com.team2.server.ServerApplicationTests"`
Expected: PASS

- [ ] **Step 10: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactory.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/IssueKakaoCalendarConsentUrlUseCase.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/DisconnectKakaoCalendarUseCase.kt \
        src/main/kotlin/com/team2/server/calendar/application/dto/KakaoCalendarConsentUrlResult.kt \
        src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionApi.kt \
        src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConnectionController.kt \
        src/main/resources/application.yml \
        src/main/resources/application-dev.yml \
        src/main/resources/application-prod.yml \
        src/test/resources/application.yml \
        src/test/kotlin/com/team2/server/calendar/application/service/KakaoConsentUrlFactoryTest.kt
git commit -m "feat: 톡캘린더 동의 URL 발급과 연동 해제 추가"
```

---

### Task 7: 동의 진입과 콜백

브라우저가 오가는 두 경로다. 이 태스크의 핵심은 **콜백에서 티켓의 사용자와 카카오가 확인해 준 계정을
대조하는 것**이다. 대조하지 않으면 브라우저의 카카오 로그인 계정이 서비스 로그인 계정과 다를 때
토큰이 엉뚱한 사용자 행에 저장된다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentCookies.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentController.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/port/CalendarUserPort.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/user/CalendarUserAdapter.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/port/KakaoAccountPort.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoAccountAdapter.kt`
- Create: `src/main/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCaseTest.kt`

**Interfaces:**
- Consumes: `ConsentTicketSigner` (Task 4), `KakaoConsentUrlFactory` (Task 6), `KakaoOAuthPort` (Task 3), `KakaoCalendarConnectionService` (Task 2)
- Produces:
  - `interface KakaoAccountPort` — `fun fetchProviderId(accessToken: String): String?`
  - `interface CalendarUserPort` — `fun findUserIdByKakaoProviderId(providerId: String): Long?`
  - `enum class ConsentOutcome { GRANTED, DENIED, ACCOUNT_MISMATCH, EXPIRED, FAILED }` (`SaveKakaoCalendarConsentUseCase` 와 같은 파일)
  - `SaveKakaoCalendarConsentUseCase` — `operator fun invoke(code: String, ticketUserId: Long): ConsentOutcome`
  - `KakaoCalendarConsentCookies` — `fun write(response, ticket, redirectUri, secure)`, `fun readTicket(request): String?`, `fun readRedirectUri(request): String?`, `fun clear(response, secure)`
  - 엔드포인트 `GET /api/v1/kakao-calendar/consent`, `GET /api/v1/kakao-calendar/consent/callback`

- [ ] **Step 1: 실패하는 UseCase 테스트 작성**

`src/test/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCaseTest.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

class SaveKakaoCalendarConsentUseCaseTest {
    private val kakaoOAuthPort: KakaoOAuthPort = mock()
    private val kakaoAccountPort: KakaoAccountPort = mock()
    private val calendarUserPort: CalendarUserPort = mock()
    private val connectionService: KakaoCalendarConnectionService = mock()
    private val urlFactory: KakaoConsentUrlFactory = mock()
    private val fixedNow = LocalDateTime.of(2026, 8, 19, 12, 0)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val useCase =
        SaveKakaoCalendarConsentUseCase(
            kakaoOAuthPort,
            kakaoAccountPort,
            calendarUserPort,
            connectionService,
            urlFactory,
            clock,
        )

    private val tokens = KakaoOAuthTokens("access-1", 21599L, "refresh-1", 5183999L)

    private fun stubHappyPath() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn("kakao-1")
        whenever(calendarUserPort.findUserIdByKakaoProviderId("kakao-1")).thenReturn(10L)
    }

    @Test
    fun `정상 동의면 연동을 저장하고 GRANTED 를 반환한다`() {
        stubHappyPath()
        whenever(connectionService.find(10L)).thenReturn(null)

        assertEquals(ConsentOutcome.GRANTED, useCase("code-1", ticketUserId = 10L))

        val captor = argumentCaptor<KakaoCalendarConnection>()
        verify(connectionService).save(captor.capture())
        assertEquals(10L, captor.firstValue.userId)
        assertEquals("access-1", captor.firstValue.accessToken)
        assertEquals(fixedNow.plusSeconds(21599), captor.firstValue.accessTokenExpiresAt)
        assertEquals(fixedNow.plusSeconds(5183999), captor.firstValue.refreshTokenExpiresAt)
    }

    @Test
    fun `이미 연동이 있으면 기존 행을 갱신한다`() {
        stubHappyPath()
        val existing =
            KakaoCalendarConnection(
                userId = 10L,
                accessToken = "old-access",
                refreshToken = "old-refresh",
                accessTokenExpiresAt = fixedNow,
                refreshTokenExpiresAt = fixedNow,
            )
        whenever(connectionService.find(10L)).thenReturn(existing)

        assertEquals(ConsentOutcome.GRANTED, useCase("code-1", ticketUserId = 10L))

        assertEquals("access-1", existing.accessToken)
        assertEquals("refresh-1", existing.refreshToken)
        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정이 티켓의 사용자와 다르면 저장하지 않는다`() {
        stubHappyPath()

        assertEquals(ConsentOutcome.ACCOUNT_MISMATCH, useCase("code-1", ticketUserId = 99L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정에 대응하는 사용자가 없으면 저장하지 않는다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn("kakao-1")
        whenever(calendarUserPort.findUserIdByKakaoProviderId("kakao-1")).thenReturn(null)

        assertEquals(ConsentOutcome.ACCOUNT_MISMATCH, useCase("code-1", ticketUserId = 10L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `토큰 교환이 거부되면 FAILED 를 반환한다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(null)

        assertEquals(ConsentOutcome.FAILED, useCase("code-1", ticketUserId = 10L))

        verify(connectionService, never()).save(any())
    }

    @Test
    fun `카카오 계정 조회가 실패하면 FAILED 를 반환한다`() {
        whenever(urlFactory.callbackUri()).thenReturn("https://api.example.com/callback")
        whenever(kakaoOAuthPort.exchange("code-1", "https://api.example.com/callback")).thenReturn(tokens)
        whenever(kakaoAccountPort.fetchProviderId("access-1")).thenReturn(null)

        assertEquals(ConsentOutcome.FAILED, useCase("code-1", ticketUserId = 10L))
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.SaveKakaoCalendarConsentUseCaseTest"`
Expected: 컴파일 실패 — `SaveKakaoCalendarConsentUseCase` 외 unresolved reference

- [ ] **Step 3: Port 둘을 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/port/KakaoAccountPort.kt`

```kotlin
package com.team2.server.calendar.application.port

/**
 * 액세스 토큰이 어느 카카오 계정의 것인지 확인한다.
 * 동의를 시작한 사용자와 실제로 동의한 계정이 같은지 대조하는 데 쓴다.
 */
interface KakaoAccountPort {
    /** 카카오 회원번호. 확인할 수 없으면 null. */
    fun fetchProviderId(accessToken: String): String?
}
```

`src/main/kotlin/com/team2/server/calendar/application/port/CalendarUserPort.kt`

```kotlin
package com.team2.server.calendar.application.port

/** 카카오 회원번호로 우리 사용자를 찾는다. 없으면 null. */
interface CalendarUserPort {
    fun findUserIdByKakaoProviderId(providerId: String): Long?
}
```

- [ ] **Step 4: 어댑터 둘을 작성한다**

`src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoAccountAdapter.kt`

```kotlin
package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.KakaoAccountPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

private const val TOKEN_INFO_PATH = "/v1/user/access_token_info"

/**
 * 일정 API 와 같은 호스트(`kapi.kakao.com`)라 `RestClient` 빈을 재사용한다.
 */
@Component
class KakaoAccountAdapter(
    @Qualifier("kakaoTalkCalendarRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : KakaoAccountPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchProviderId(accessToken: String): String? {
        val response =
            try {
                restClient
                    .get()
                    .uri(TOKEN_INFO_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .exchange { _, res ->
                        ResponseEntity.status(res.statusCode).body(res.bodyTo(String::class.java))
                    }
            } catch (e: RestClientException) {
                log.warn("카카오 토큰 정보 조회 실패", e)
                return null
            }
        if (!response.statusCode.is2xxSuccessful) {
            log.warn("카카오 토큰 정보 조회 거부. status={}", response.statusCode.value())
            return null
        }
        val parsed = runCatching { objectMapper.readValue(response.body ?: "", Map::class.java) }.getOrNull()
        return (parsed?.get("id") as? Number)?.toLong()?.toString()
    }
}
```

`src/main/kotlin/com/team2/server/calendar/infrastructure/user/CalendarUserAdapter.kt`

```kotlin
package com.team2.server.calendar.infrastructure.user

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class CalendarUserAdapter(
    private val userRepository: UserRepository,
) : CalendarUserPort {
    override fun findUserIdByKakaoProviderId(providerId: String): Long? =
        userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)?.id
}
```

- [ ] **Step 5: 저장 UseCase 를 작성한다**

`src/main/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt`

```kotlin
package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.calendar.application.port.KakaoAccountPort
import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.service.KakaoCalendarConnectionService
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

enum class ConsentOutcome {
    GRANTED,
    DENIED,
    ACCOUNT_MISMATCH,
    EXPIRED,
    FAILED,
}

/**
 * 동의 콜백에서 받은 인가 코드를 토큰으로 바꿔 연동을 저장한다.
 *
 * 티켓이 지정한 사용자와 카카오가 확인해 준 계정이 일치할 때만 저장한다. 대조하지 않으면 브라우저의
 * 카카오 로그인 계정이 서비스 로그인 계정과 다를 때(공용 PC, 계정 여러 개) 토큰이 엉뚱한 사용자 행에
 * 저장되어, 한쪽은 연동했는데도 계속 동의를 요구받고 다른 쪽 캘린더에는 모르는 일정이 등록된다.
 *
 * 카카오 계정에 대응하는 서비스 사용자가 없는 경우도 같은 실패로 다룬다. 추가 동의는 기존 사용자의
 * 권한을 확장하는 경로이지 가입 경로가 아니다.
 */
@Service
class SaveKakaoCalendarConsentUseCase(
    private val kakaoOAuthPort: KakaoOAuthPort,
    private val kakaoAccountPort: KakaoAccountPort,
    private val calendarUserPort: CalendarUserPort,
    private val kakaoCalendarConnectionService: KakaoCalendarConnectionService,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        code: String,
        ticketUserId: Long,
    ): ConsentOutcome {
        val tokens = kakaoOAuthPort.exchange(code, kakaoConsentUrlFactory.callbackUri()) ?: return ConsentOutcome.FAILED
        val providerId = kakaoAccountPort.fetchProviderId(tokens.accessToken) ?: return ConsentOutcome.FAILED
        if (calendarUserPort.findUserIdByKakaoProviderId(providerId) != ticketUserId) {
            return ConsentOutcome.ACCOUNT_MISMATCH
        }

        val now = LocalDateTime.now(clock)
        val accessTokenExpiresAt = now.plusSeconds(tokens.accessTokenExpiresInSeconds)
        val refreshTokenExpiresAt = now.plusSeconds(tokens.refreshTokenExpiresInSeconds ?: 0L)
        val existing = kakaoCalendarConnectionService.find(ticketUserId)
        if (existing != null) {
            existing.applyRefreshed(
                accessToken = tokens.accessToken,
                accessTokenExpiresAt = accessTokenExpiresAt,
                refreshToken = tokens.refreshToken,
                refreshTokenExpiresAt = refreshTokenExpiresAt,
            )
            return ConsentOutcome.GRANTED
        }
        kakaoCalendarConnectionService.save(
            KakaoCalendarConnection(
                userId = ticketUserId,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: return ConsentOutcome.FAILED,
                accessTokenExpiresAt = accessTokenExpiresAt,
                refreshTokenExpiresAt = refreshTokenExpiresAt,
            ),
        )
        return ConsentOutcome.GRANTED
    }
}
```

- [ ] **Step 6: 쿠키 헬퍼를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentCookies.kt`

기존 `com.team2.server.auth.oauth2.OAuth2RedirectUriCookies` 를 먼저 열어 쿠키 속성 설정 방식
(`path`, `httpOnly`, `secure`, `maxAge`)을 확인하고 같은 모양으로 맞춘다.

```kotlin
package com.team2.server.calendar.api

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val TICKET_COOKIE = "kakao_calendar_consent_ticket"
private const val REDIRECT_URI_COOKIE = "kakao_calendar_consent_redirect_uri"
private const val MAX_AGE_SECONDS = 300

/**
 * 동의 진입에서 콜백까지 티켓과 복귀 주소를 나른다.
 *
 * 서버 메모리가 아니라 쿠키에 두는 이유는 blue/green 배포에서 콜백이 다른 인스턴스로 도착해도
 * 검증이 성립해야 하기 때문이다. 수명은 티켓과 같은 5분이다.
 */
object KakaoCalendarConsentCookies {
    fun write(
        response: HttpServletResponse,
        ticket: String,
        redirectUri: String,
        secure: Boolean,
    ) {
        response.addCookie(cookie(TICKET_COOKIE, ticket, MAX_AGE_SECONDS, secure))
        response.addCookie(cookie(REDIRECT_URI_COOKIE, redirectUri, MAX_AGE_SECONDS, secure))
    }

    fun readTicket(request: HttpServletRequest): String? = read(request, TICKET_COOKIE)

    fun readRedirectUri(request: HttpServletRequest): String? = read(request, REDIRECT_URI_COOKIE)

    fun clear(
        response: HttpServletResponse,
        secure: Boolean,
    ) {
        response.addCookie(cookie(TICKET_COOKIE, "", 0, secure))
        response.addCookie(cookie(REDIRECT_URI_COOKIE, "", 0, secure))
    }

    private fun read(
        request: HttpServletRequest,
        name: String,
    ): String? = request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() }

    private fun cookie(
        name: String,
        value: String,
        maxAge: Int,
        secure: Boolean,
    ): Cookie =
        Cookie(name, value).apply {
            path = "/"
            isHttpOnly = true
            this.secure = secure
            this.maxAge = maxAge
        }
}
```

- [ ] **Step 7: 동의 컨트롤러를 작성한다**

`src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentController.kt`

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.config.OAuth2Properties
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.application.service.KakaoConsentUrlFactory
import com.team2.server.calendar.application.usecase.ConsentOutcome
import com.team2.server.calendar.application.usecase.SaveKakaoCalendarConsentUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

private const val RESULT_PARAM = "calendarConsent"

/**
 * 브라우저 내비게이션으로 오가는 두 경로다. 서비스 JWT 가 실리지 않으므로 `SecurityConfig` 에서
 * 인증 예외로 두되, 진입은 서명된 티켓과 `redirect_uri` 화이트리스트로, 콜백은 쿠키 티켓과 `state`
 * 대조로 보호한다.
 */
@RestController
@RequestMapping("/api/v1/kakao-calendar/consent")
class KakaoCalendarConsentController(
    private val consentTicketSigner: ConsentTicketSigner,
    private val kakaoConsentUrlFactory: KakaoConsentUrlFactory,
    private val saveKakaoCalendarConsentUseCase: SaveKakaoCalendarConsentUseCase,
    private val oAuth2Properties: OAuth2Properties,
) {
    @GetMapping
    fun enter(
        @RequestParam ticket: String,
        @RequestParam("redirect_uri") redirectUri: String,
        response: HttpServletResponse,
    ): RedirectView {
        if (consentTicketSigner.verify(ticket) == null) {
            return RedirectView(resultUrl(fallbackRedirectUri(), ConsentOutcome.EXPIRED))
        }
        if (!oAuth2Properties.authorizedRedirectUris.contains(redirectUri)) {
            return RedirectView(resultUrl(fallbackRedirectUri(), ConsentOutcome.FAILED))
        }
        KakaoCalendarConsentCookies.write(response, ticket, redirectUri, oAuth2Properties.cookieSecure)
        return RedirectView(kakaoConsentUrlFactory.kakaoAuthorizeUrl(ticket))
    }

    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        val redirectUri = KakaoCalendarConsentCookies.readRedirectUri(request) ?: fallbackRedirectUri()
        val cookieTicket = KakaoCalendarConsentCookies.readTicket(request)
        KakaoCalendarConsentCookies.clear(response, oAuth2Properties.cookieSecure)

        if (code.isNullOrBlank()) {
            return RedirectView(resultUrl(redirectUri, ConsentOutcome.DENIED))
        }
        if (cookieTicket == null || state == null || cookieTicket != state) {
            return RedirectView(resultUrl(redirectUri, ConsentOutcome.EXPIRED))
        }
        val userId =
            consentTicketSigner.verify(cookieTicket)
                ?: return RedirectView(resultUrl(redirectUri, ConsentOutcome.EXPIRED))

        val outcome =
            runCatching { saveKakaoCalendarConsentUseCase(code, userId) }
                .getOrDefault(ConsentOutcome.FAILED)
        return RedirectView(resultUrl(redirectUri, outcome))
    }

    /** 복귀 주소를 알 수 없을 때 쓸 기본값. 화이트리스트의 첫 항목이다. */
    private fun fallbackRedirectUri(): String = oAuth2Properties.authorizedRedirectUris.first()

    private fun resultUrl(
        redirectUri: String,
        outcome: ConsentOutcome,
    ): String =
        UriComponentsBuilder
            .fromUriString(redirectUri)
            .queryParam(RESULT_PARAM, outcome.name.lowercase())
            .encode()
            .toUriString()
}
```

- [ ] **Step 8: SecurityConfig 에 인증 예외를 추가한다**

`src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` 의 첫 `requestMatchers(...)` 목록
(`"/api/dev/**"` 가 있는 블록)에 아래 한 줄을 더한다.

```kotlin
                        "/api/v1/kakao-calendar/consent/**",
```

- [ ] **Step 9: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.application.usecase.SaveKakaoCalendarConsentUseCaseTest"`
Expected: PASS (6 tests)

- [ ] **Step 10: Spring 컨텍스트가 뜨는지 확인한다**

Run: `./gradlew test --tests "com.team2.server.ServerApplicationTests"`
Expected: PASS

- [ ] **Step 11: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/application/port/KakaoAccountPort.kt \
        src/main/kotlin/com/team2/server/calendar/application/port/CalendarUserPort.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/kakao/KakaoAccountAdapter.kt \
        src/main/kotlin/com/team2/server/calendar/infrastructure/user/CalendarUserAdapter.kt \
        src/main/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCase.kt \
        src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentCookies.kt \
        src/main/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentController.kt \
        src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt \
        src/test/kotlin/com/team2/server/calendar/application/usecase/SaveKakaoCalendarConsentUseCaseTest.kt
git commit -m "feat: 톡캘린더 동의 진입과 콜백 추가"
```

---

### Task 8: 등록 엔드포인트 개편

토큰의 출처를 헤더에서 저장된 연동으로 바꾼다. **`RegisterPartyTalkCalendarEventUseCase` 와 그 테스트는
건드리지 않는다** — 이미 `kakaoAccessToken` 을 커맨드로 받고 있어 출처만 달라진다.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt`
- Modify: `src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Modify: `src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt`

**Interfaces:**
- Consumes: `ResolveKakaoCalendarAccessTokenUseCase` (Task 5), 기존 `RegisterPartyTalkCalendarEventUseCase`
- Produces: `POST /api/v1/parties/{partyId}/talk-calendar` — 헤더 없음, 연동 없으면 403 `KAKAO_CALENDAR_CONSENT_REQUIRED`

- [ ] **Step 1: 기존 통합 테스트를 새 계약에 맞게 고친다**

`src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt` 에서 다음을 수정한다.

1. 모든 요청에서 `header("X-Kakao-Access-Token", "kakao-token")` 줄을 **삭제**한다.
2. `카카오 액세스 토큰 헤더가 없으면 400` 테스트를 아래 테스트로 **교체**한다.

```kotlin
        @Test
        fun `연동이 없으면 403 과 동의 필요 코드를 반환한다`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("KAKAO_CALENDAR_CONSENT_REQUIRED") }
                }
        }
```

3. 성공 경로 테스트 세 개(`호스트가 유효한 토큰으로...`, `이미 등록한 파티를...`, `카카오로 나가는 일정 시각은...`)는
   저장된 연동이 있어야 동작한다. Task 9 에서 연동 픽스처를 만들며 되살리므로, 지금은 `@org.junit.jupiter.api.Disabled("Task 9 에서 연동 픽스처와 함께 복구")`
   를 붙여 둔다. **Task 9 에서 반드시 제거한다.**

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.TalkCalendarControllerTest"`
Expected: `연동이 없으면 403...` 이 400 을 받아 실패

- [ ] **Step 3: 컨트롤러를 고친다**

`src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt` 전체를 교체한다.

```kotlin
package com.team2.server.calendar.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventCommand
import com.team2.server.calendar.application.dto.RegisterPartyTalkCalendarEventResult
import com.team2.server.calendar.application.usecase.RegisterPartyTalkCalendarEventUseCase
import com.team2.server.calendar.application.usecase.ResolveKakaoCalendarAccessTokenUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class TalkCalendarController(
    private val resolveKakaoCalendarAccessTokenUseCase: ResolveKakaoCalendarAccessTokenUseCase,
    private val registerPartyTalkCalendarEventUseCase: RegisterPartyTalkCalendarEventUseCase,
) : TalkCalendarApi {
    /**
     * 토큰 확보와 일정 등록은 서로 다른 트랜잭션이다.
     * 여기서 순서대로 부르는 이유는 한 UseCase 안에서 나누면 같은 빈 자기호출이 되어
     * Spring 프록시를 타지 않고, 그러면 트랜잭션이 아예 걸리지 않기 때문이다.
     */
    @PostMapping("/{partyId}/talk-calendar")
    override fun registerPartyEvent(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<RegisterPartyTalkCalendarEventResult> {
        val accessToken =
            resolveKakaoCalendarAccessTokenUseCase(principal.userId)
                ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
        return ApiResponse.success(
            HttpStatus.OK,
            registerPartyTalkCalendarEventUseCase(
                RegisterPartyTalkCalendarEventCommand(
                    partyId = partyId,
                    userId = principal.userId,
                    kakaoAccessToken = accessToken,
                ),
            ),
        )
    }
}
```

- [ ] **Step 4: Swagger 스펙을 고친다**

`src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt` 에서:

1. `registerPartyEvent` 시그니처에서 `kakaoAccessToken: String?` 파라미터와 그 `@Parameter` 블록을 삭제한다.
2. `import io.swagger.v3.oas.annotations.enums.ParameterIn` 을 삭제한다.
3. `@Operation(description = ...)` 의 **카카오 액세스 토큰** 문단을 아래로 교체한다.

```
**동의**
서버가 저장한 카카오 토큰을 사용한다. 저장된 연동이 없거나 만료됐으면 403 `KAKAO_CALENDAR_CONSENT_REQUIRED` 를
반환하므로, 클라이언트는 `GET /api/v1/me/talk-calendar-connection/consent-url` 로 동의 URL 을 받아
브라우저를 그리로 보낸다. 동의를 마치고 돌아오면 이 API 를 다시 호출한다.
```

4. 400 응답 블록(`KAKAO_ACCESS_TOKEN_REQUIRED` 예시)을 통째로 삭제한다.
5. 401 응답의 `KAKAO_TOKEN_INVALID` 예시를 삭제한다. 저장된 토큰이 거부되면 갱신을 거쳐 403 으로 수렴하므로
   클라이언트에 401 로 나가지 않는다.

- [ ] **Step 5: 안 쓰는 ErrorCode 를 삭제한다**

`src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` 에서 아래 줄을 삭제한다.

```kotlin
    KAKAO_ACCESS_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "카카오 액세스 토큰이 필요합니다"),
```

`KAKAO_TOKEN_INVALID` 는 어댑터가 카카오 401 을 구분하는 내부 신호로 계속 쓰이므로 **남긴다.**

카카오가 scope 부족(`-402`, HTTP 403)을 돌려주는 경우는 기존 어댑터가 이미
`KAKAO_CALENDAR_CONSENT_REQUIRED` 로 변환하므로 클라이언트 동작은 그대로 맞다. 스펙은 이때 연동을
지우라고 적었으나 **지우지 않는다.** 이 상황의 토큰은 유효하고 scope 만 없는 것이라, 사용자가 다시
동의하면 같은 행이 갱신되며 정상화된다. 지워도 결과가 같은데 한 번 더 쓰는 일이라 하지 않는다.

- [ ] **Step 6: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.TalkCalendarControllerTest"`
Expected: PASS (Disabled 3개 제외)

- [ ] **Step 7: 전체 빌드로 회귀를 확인한다**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. `KAKAO_ACCESS_TOKEN_REQUIRED` 를 참조하는 곳이 남아 있으면 컴파일이 깨지므로 그때 함께 정리한다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/team2/server/calendar/api/TalkCalendarController.kt \
        src/main/kotlin/com/team2/server/calendar/api/TalkCalendarApi.kt \
        src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt \
        src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt
git commit -m "feat: 저장된 토큰으로 톡캘린더 등록하도록 변경"
```

---

### Task 9: 동의부터 등록까지 통합 테스트

카카오 인증 서버와 일정 API 를 루프백 스텁으로 세우고 전 구간을 한 흐름으로 검증한다.

이 태스크가 필요한 이유는 앞선 작업에서 두 번 확인됐다. 카카오 시각 포맷을 잘못 잡은 것과 오류 응답
본문이 유실되던 것 모두 단위 테스트를 통과했고 실제 HTTP 호출에서만 드러났다. `MockRestServiceServer`
는 응답 본문을 버퍼링하므로 전송 계층의 차이를 재현하지 못한다.

**Files:**
- Create: `src/test/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentFlowTest.kt`
- Modify: `src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt`

**Interfaces:**
- Consumes: Task 1~8 전부
- Produces: 없음 (테스트만)

- [ ] **Step 1: 동의 플로우 통합 테스트를 작성한다**

`src/test/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentFlowTest.kt`

스텁 서버는 `TalkCalendarControllerTest` 의 companion object 패턴을 그대로 따르되 포트가 두 개다
(`19595` 일정 API, `19596` 인증 서버). 테스트 프로파일의 `kakao.talk-calendar.base-url` 과
`kakao.auth.base-url` 이 각각 그 포트를 가리키고 있어야 한다.

```kotlin
package com.team2.server.calendar.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class KakaoCalendarConsentFlowTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val userRepository: UserRepository,
        private val connectionRepository: KakaoCalendarConnectionRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
        private val consentTicketSigner: ConsentTicketSigner,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            kakaoUserId = "kakao-1"
        }

        private fun saveUser(providerId: String = "kakao-1"): User =
            userRepository.save(
                User(
                    name = "호스트",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = "$providerId@test.local",
                ),
            )

        @Test
        fun `동의 URL 발급은 우리 진입 주소와 티켓을 담는다`() {
            val user = saveUser()

            mockMvc
                .get("/api/v1/me/talk-calendar-connection/consent-url") {
                    header("Authorization", "Bearer ${tokenProvider.issue(user)}")
                    param("redirectUri", "http://localhost:5173/oauth/redirect")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.consentUrl") { exists() }
                }
        }

        @Test
        fun `진입 요청은 카카오 인가 주소로 리다이렉트하고 쿠키를 심는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", ticket)
                    param("redirect_uri", "http://localhost:5173/oauth/redirect")
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("http://localhost:19596/oauth/authorize?*")
                    cookie { exists("kakao_calendar_consent_ticket") }
                }
        }

        @Test
        fun `위조된 티켓으로 진입하면 expired 로 돌려보낸다`() {
            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", "forged.ticket")
                    param("redirect_uri", "http://localhost:5173/oauth/redirect")
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("*calendarConsent=expired*")
                }
        }

        @Test
        fun `콜백은 토큰을 저장하고 granted 로 돌려보낸다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            "http://localhost:5173/oauth/redirect",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("*calendarConsent=granted*")
                }

            val saved = connectionRepository.findAll().single()
            assertEquals(user.id, saved.userId)
            assertEquals("stub-access", saved.accessToken)
            assertEquals("stub-refresh", saved.refreshToken)
        }

        @Test
        fun `카카오 계정이 티켓의 사용자와 다르면 저장하지 않는다`() {
            val user = saveUser(providerId = "kakao-1")
            val ticket = consentTicketSigner.issue(user.id)
            kakaoUserId = "kakao-999"

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            "http://localhost:5173/oauth/redirect",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("*calendarConsent=account_mismatch*")
                }

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        @Test
        fun `state 와 쿠키 티켓이 다르면 저장하지 않는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", "other-ticket"))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            "http://localhost:5173/oauth/redirect",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("*calendarConsent=expired*")
                }

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        @Test
        fun `사용자가 동의를 거부하면 denied 로 돌려보낸다`() {
            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("error", "access_denied")
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            "http://localhost:5173/oauth/redirect",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("*calendarConsent=denied*")
                }
        }

        @Test
        fun `연동 해제는 저장된 토큰을 지운다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)
            mockMvc.get("/api/v1/kakao-calendar/consent/callback") {
                param("code", "auth-code")
                param("state", ticket)
                cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                cookie(
                    jakarta.servlet.http.Cookie(
                        "kakao_calendar_consent_redirect_uri",
                        "http://localhost:5173/oauth/redirect",
                    ),
                )
            }
            assertNotNull(connectionRepository.findAll().singleOrNull())

            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/me/talk-calendar-connection")
                        .header("Authorization", "Bearer ${tokenProvider.issue(user)}"),
                ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent)

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        companion object {
            private const val AUTH_STUB_PORT = 19596

            /** 테스트가 카카오 계정을 바꿔 계정 불일치를 만들 수 있게 한다. */
            @JvmStatic
            private var kakaoUserId: String = "kakao-1"

            private lateinit var authStub: HttpServer

            @JvmStatic
            @BeforeAll
            fun startStub() {
                authStub =
                    HttpServer.create(InetSocketAddress("127.0.0.1", AUTH_STUB_PORT), 0).apply {
                        createContext("/oauth/token") {
                            respond(
                                it,
                                """
                                {
                                  "access_token": "stub-access",
                                  "expires_in": 21599,
                                  "refresh_token": "stub-refresh",
                                  "refresh_token_expires_in": 5183999
                                }
                                """.trimIndent(),
                            )
                        }
                        start()
                    }
            }

            @JvmStatic
            @AfterAll
            fun stopStub() {
                authStub.stop(0)
            }

            private fun respond(
                exchange: HttpExchange,
                body: String,
            ) {
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
    }
```

**스텁을 공용 지원 클래스로 뺀다.** 두 테스트 클래스가 같은 포트를 각자 열면 충돌하므로,
`src/test/kotlin/com/team2/server/support/KakaoStubServers.kt` 에 참조 계수 방식으로 한 번만 열고
마지막 사용자가 닫는 오브젝트를 만든다.

```kotlin
package com.team2.server.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 카카오 API 두 호스트를 흉내내는 루프백 스텁.
 *
 * 테스트 프로파일의 `kakao.talk-calendar.base-url`(19595) 과 `kakao.auth.base-url`(19596) 이
 * 이 포트를 가리킨다. 여러 테스트 클래스가 공유하므로 참조 계수로 한 번만 열고 마지막에 닫는다.
 */
object KakaoStubServers {
    const val CALENDAR_PORT = 19595
    const val AUTH_PORT = 19596

    /** 경로별 마지막 요청 바디. 카카오로 나간 페이로드를 검증할 때 쓴다. */
    val requests = ConcurrentHashMap<String, String>()

    /** 테스트가 카카오 회원번호를 바꿔 계정 불일치를 만들 수 있게 한다. 숫자여야 한다. */
    @Volatile
    var kakaoUserId: Long = 1L

    private var refCount = 0
    private var calendar: HttpServer? = null
    private var auth: HttpServer? = null

    @Synchronized
    fun start() {
        if (refCount++ > 0) return
        calendar =
            HttpServer.create(InetSocketAddress("127.0.0.1", CALENDAR_PORT), 0).apply {
                createContext("/v2/api/calendar/create/event") { respond(it, EVENT_BODY) }
                createContext("/v2/api/calendar/update/event/host") { respond(it, EVENT_BODY) }
                createContext("/v1/user/access_token_info") { respond(it, "{\"id\":$kakaoUserId}") }
                start()
            }
        auth =
            HttpServer.create(InetSocketAddress("127.0.0.1", AUTH_PORT), 0).apply {
                createContext("/oauth/token") { respond(it, TOKEN_BODY) }
                start()
            }
    }

    @Synchronized
    fun stop() {
        if (--refCount > 0) return
        calendar?.stop(0)
        auth?.stop(0)
        calendar = null
        auth = null
    }

    fun reset() {
        requests.clear()
        kakaoUserId = 1L
    }

    private fun respond(
        exchange: HttpExchange,
        body: String,
    ) {
        requests[exchange.requestURI.path] = exchange.requestBody.readBytes().decodeToString()
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private const val EVENT_BODY = "{\"event_id\":\"stub-event-1\"}"
    private const val TOKEN_BODY =
        "{\"access_token\":\"stub-access\",\"expires_in\":21599," +
            "\"refresh_token\":\"stub-refresh\",\"refresh_token_expires_in\":5183999}"
}
```

두 테스트 클래스의 `companion object` 는 각자 스텁을 열지 말고 아래만 둔다.

```kotlin
        companion object {
            @JvmStatic
            @BeforeAll
            fun startStub() = KakaoStubServers.start()

            @JvmStatic
            @AfterAll
            fun stopStub() = KakaoStubServers.stop()
        }
```

그리고 위 테스트 코드의 `kakaoUserId = "kakao-1"` 은 `KakaoStubServers.reset()` 으로,
`kakaoUserId = "kakao-999"` 는 `KakaoStubServers.kakaoUserId = 999L` 로 바꾼다.
어댑터가 `(parsed["id"] as? Number)` 로 읽으므로 **회원번호는 숫자여야 한다.** 사용자 픽스처의
`providerId` 도 `"1"` 처럼 숫자 문자열로 맞춘다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.KakaoCalendarConsentFlowTest"`
Expected: 실패 — 스텁 경로나 픽스처가 아직 맞지 않는다. 위 주의 두 가지를 반영해 통과시킨다.

- [ ] **Step 3: Task 8 에서 비활성화한 성공 경로 테스트를 되살린다**

`src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt` 에서 `@Disabled` 를
모두 제거하고, 각 성공 경로 테스트가 시작될 때 연동을 미리 저장하도록 픽스처를 추가한다.

```kotlin
        private fun saveConnection(userId: Long) {
            connectionRepository.save(
                com.team2.server.calendar.domain.entity.KakaoCalendarConnection(
                    userId = userId,
                    accessToken = "kakao-token",
                    refreshToken = "kakao-refresh",
                    accessTokenExpiresAt = LocalDateTime.now().plusHours(6),
                    refreshTokenExpiresAt = LocalDateTime.now().plusMonths(2),
                ),
            )
        }
```

생성자에 `private val connectionRepository: KakaoCalendarConnectionRepository` 를 추가하고,
성공 경로 테스트들에서 `saveHostAndParty(...)` 직후 `saveConnection(fixture.userId)` 를 부른다.
`HostFixture` 에 `userId` 가 없으면 필드를 추가한다.

- [ ] **Step 4: 두 테스트 클래스를 함께 실행한다**

Run: `./gradlew test --tests "com.team2.server.calendar.api.*"`
Expected: PASS. 포트 충돌이 나면 스텁 시작 위치를 한 곳으로 모은다.

- [ ] **Step 5: 전체 빌드와 컨테이너 누수를 확인한다**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `docker ps -a --filter "label=org.testcontainers"`
Expected: 잔존 컨테이너 0개

- [ ] **Step 6: 커밋**

```bash
git add src/test/kotlin/com/team2/server/calendar/api/KakaoCalendarConsentFlowTest.kt \
        src/test/kotlin/com/team2/server/calendar/api/TalkCalendarControllerTest.kt
git commit -m "test: 톡캘린더 동의 플로우 통합 테스트 추가"
```

---

## 남은 확인 사항

코드로 해결되지 않는 것들이다.

- **삭제된 일정을 수정할 때 카카오가 주는 응답 코드.** 현재 어댑터는 404 를 가정하고 재생성 경로를 탄다.
  `kapi.kakao.com` 은 대체로 400·401·403 에 음수 `code` 를 실어 보내는 패턴이라 404 가 아닐 수 있다.
  앱 멤버 계정으로 일정을 하나 만들고 카카오톡에서 지운 뒤 수정 API 를 호출해 확인한다.
  틀리면 `KakaoTalkCalendarAdapter.updateEvent` 의 분기 한 줄을 고친다.
- **톡캘린더 추가 기능 신청.** 권한을 받기 전에는 앱 멤버만 호출할 수 있다. 개발과 팀 내 검증은
  지금 가능하고, 일반 사용자 공개 전에 [앱] > [추가 기능 신청] 이 필요하다.
- **시크릿에 `app.crypto.token-secret` 추가.** `openssl rand -base64 32` 로 만들어
  `application-secret-dev.yml` 과 `application-secret-prod.yml` 에 서로 다른 값으로 넣는다.
- **카카오 콘솔 Redirect URI 등록.** `https://api.hapalin.com/api/v1/kakao-calendar/consent/callback`,
  dev 와 로컬(`http://localhost:8080/...`) 각각. 등록하지 않으면 `KOE006` 으로 거부된다.
