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
  # 일정 설명의 초대 링크(/invite/{token})를 조립할 프론트엔드 origin
  # 기본값은 로컬 프론트 개발 서버(Vite). 배포 환경은 application-dev/prod.yml 에서 지정한다
  web-base-url: ${APP_WEB_BASE_URL:http://localhost:5173}
  crypto:
    # 저장된 카카오 토큰 암호화 키. Base64 32바이트. 시크릿 저장소에서 환경별로 주입한다
    token-secret: ${APP_CRYPTO_TOKEN_SECRET:}
```

`src/test/resources/application.yml` 의 `app:` 블록에도 고정 키를 추가한다.

```yaml
  crypto:
    token-secret: dGVzdC1jcnlwdG8ta2V5LTMyLWJ5dGVzLWZvci1hZXMtZ2NtISE=
```

- [ ] **Step 6: 테스트 키가 32바이트인지 확인한다**

Run: `echo -n 'dGVzdC1jcnlwdG8ta2V5LTMyLWJ5dGVzLWZvci1hZXMtZ2NtISE=' | base64 -d | wc -c`
Expected: `36` 이 아니라 `32`. 32가 아니면 32바이트 값을 새로 만들어 넣는다 (`openssl rand -base64 32`).

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
    constructor(
        restClient: RestClient,
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

두 생성자 중 Spring 이 어느 것을 고르는지 확인해야 한다. `ClientRegistrationRepository` 를 받는 쪽이
선택되지 않으면 `clientId` 를 String 빈으로 찾다가 실패한다. 실패하면 `ClientRegistrationRepository`
생성자에 `@Autowired` 를 붙인다.

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

## 미작성 태스크 (작성 중단 지점)

Task 1~4 까지 작성됐다. 아래 다섯은 아직 본문이 없으며, 이어서 작성해야 한다.
설계 근거는 스펙에 모두 있으므로 그것을 따른다.

- **Task 5 — 토큰 확보 UseCase** `ResolveKakaoCalendarAccessTokenUseCase(userId): String?`.
  `@Transactional`. 잠금 조회 → 사용 가능하면 그대로 반환 → 리프레시 만료면 연동 삭제 후 `null` →
  갱신 호출이 `null` 이면 연동 삭제 후 `null` → 갱신 성공이면 반영 후 새 토큰 반환.
  **예외를 던지지 않는 것이 핵심이다.** 던지면 같은 트랜잭션이 롤백되며 삭제가 되감긴다.
- **Task 6 — 동의 URL 조립과 발급** `KakaoConsentUrlFactory`(우리 진입 URL + 카카오 인가 URL,
  scope 는 `talk_calendar`), `IssueKakaoCalendarConsentUrlUseCase`,
  `GET /api/v1/me/talk-calendar-connection/consent-url`, `DELETE /api/v1/me/talk-calendar-connection`.
- **Task 7 — 동의 진입·콜백 컨트롤러** 티켓·`redirect_uri` 검증 후 쿠키 저장 → 카카오 인가로 리다이렉트.
  콜백은 쿠키 티켓과 `state` 대조, 코드 교환, **티켓의 userId 와 카카오 계정 대조**, 연동 저장.
  복귀 파라미터 `granted` / `denied` / `account_mismatch` / `expired` / `failed`.
  `SecurityConfig` 에 두 경로 `permitAll` 추가.
- **Task 8 — 등록 엔드포인트 개편** 컨트롤러에서 `X-Kakao-Access-Token` 제거, Task 5 를 먼저 호출해
  `null` 이면 403 `KAKAO_CALENDAR_CONSENT_REQUIRED`. `KAKAO_ACCESS_TOKEN_REQUIRED` 삭제. Swagger 갱신.
  `RegisterPartyTalkCalendarEventUseCase` 와 그 테스트는 그대로 둔다.
- **Task 9 — 통합 테스트** 루프백 스텁 둘(`19595` 일정 API, `19596` 인증 서버)을 세우고
  동의 진입 → 콜백 → 저장 → 등록까지 한 흐름으로 검증. 계정 불일치와 티켓 만료 경로도 함께.
