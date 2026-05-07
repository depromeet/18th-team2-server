# 카카오 OAuth 로그인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Security `oauth2-client` 기반 카카오 소셜 로그인 + 자체 JWT(24h) 발급으로 stateless API 인증 구축. 추가 provider(GOOGLE/APPLE/NAVER)를 쉽게 확장할 수 있는 전략 패턴 적용.

**Architecture:** 서버 redirect 플로우. `/oauth2/authorization/kakao` → 카카오 → `/login/oauth2/code/kakao` → `CustomOAuth2UserService`가 사용자 upsert → `OAuth2SuccessHandler`가 JWT 발급 후 화이트리스트 redirect URI로 302. 이후 API는 `Authorization: Bearer <jwt>` → `JwtAuthenticationFilter`가 인증.

**Tech Stack:** Kotlin 2.3, Spring Boot 4.0, Spring Security 6+, `spring-boot-starter-oauth2-client`, jjwt 0.12.6, JPA/Hibernate, JUnit 5, MockMvc, spring-security-test, H2 (test).

**Spec Reference:** `docs/superpowers/specs/2026-04-26-kakao-oauth-login-design.md`

---

## File Structure

### 신규 생성

| 경로 | 책임 |
|---|---|
| `src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt` | `app.jwt.*` ConfigurationProperties |
| `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` | SecurityFilterChain, OAuth2Login, JWT 필터 등록, CORS |
| `src/main/kotlin/com/team2/server/auth/jwt/JwtTokenProvider.kt` | JWT 생성/검증 (jjwt 래퍼) |
| `src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilter.kt` | OncePerRequestFilter — Bearer 헤더 처리 |
| `src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationEntryPoint.kt` | 인증 실패 401 응답 (`ErrorResponse` 포맷) |
| `src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2Attributes.kt` | provider 응답 파싱 공통 인터페이스 |
| `src/main/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributes.kt` | KAKAO 응답 파싱 구현 |
| `src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactory.kt` | registrationId → OAuth2Attributes 디스패치 |
| `src/main/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserService.kt` | DefaultOAuth2UserService 확장, 사용자 upsert |
| `src/main/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandler.kt` | JWT 발급 + 화이트리스트 redirect |
| `src/main/kotlin/com/team2/server/auth/oauth2/OAuth2FailureHandler.kt` | 실패 redirect |
| `src/main/kotlin/com/team2/server/auth/principal/UserPrincipal.kt` | `OAuth2User` + `UserDetails` 통합 객체 |
| `src/main/kotlin/com/team2/server/auth/controller/AuthController.kt` | `/api/auth/me` |
| `src/main/kotlin/com/team2/server/auth/controller/UserResponse.kt` | `/api/auth/me` 응답 DTO |
| `src/main/kotlin/com/team2/server/user/repository/UserRepository.kt` | JpaRepository + `findByProviderAndProviderId` |
| `src/test/kotlin/com/team2/server/auth/jwt/JwtTokenProviderTest.kt` | 단위 테스트 |
| `src/test/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributesTest.kt` | 단위 테스트 |
| `src/test/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactoryTest.kt` | 단위 테스트 |
| `src/test/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserServiceTest.kt` | 단위 테스트 (UserRepository 페이크) |
| `src/test/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandlerTest.kt` | 단위 테스트 |
| `src/test/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilterTest.kt` | MockMvc 슬라이스 |
| `src/test/kotlin/com/team2/server/auth/SecurityIntegrationTest.kt` | `@SpringBootTest` + MockMvc |
| `src/test/kotlin/com/team2/server/user/UserRepositoryTest.kt` | `@DataJpaTest` |
| `src/test/kotlin/com/team2/server/auth/FakeUserRepository.kt` | 테스트 페이크 |

### 수정

| 경로 | 변경 내용 |
|---|---|
| `build.gradle.kts` | spring-security, oauth2-client, jjwt, spring-security-test 의존성 추가 |
| `src/main/kotlin/com/team2/server/ServerApplication.kt` | `@ConfigurationPropertiesScan` 추가 |
| `src/main/kotlin/com/team2/server/user/entity/User.kt` | `providerId` 필드 + `(provider, providerId)` unique 제약 |
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | AUTH_* 코드 5개 추가 |
| `src/test/resources/application.yml` | 테스트용 oauth client 더미 등록 + `app.jwt.*` |

### 외부 (서브모듈, 별도 PR/푸시)

| 경로 | 변경 내용 |
|---|---|
| `config/secret/application-secret.yml` | kakao registration/provider, app.jwt, app.oauth2 |
| `config/secret/application-secret-dev.yml` | dev 카카오 키 / JWT 시크릿 / redirect URI |
| `config/secret/application-secret-prod.yml` | prod 카카오 키 / JWT 시크릿 / redirect URI |

---

## Task Order Rationale

의존 그래프 따라 진행:
1. 인프라 (deps, ErrorCode, User entity, Repository) → 2. 토큰 (JwtProperties, JwtTokenProvider) → 3. Principal → 4. OAuth 파서 (Attributes, Factory) → 5. OAuth 서비스 (CustomOAuth2UserService) → 6. 필터/핸들러 (EntryPoint, JwtFilter, Success/Failure) → 7. SecurityConfig → 8. Controller → 9. 통합 테스트 → 10. yml/시크릿/문서.

각 Task 마지막에 `./gradlew compileKotlin` 또는 해당 테스트만 실행하여 빠른 피드백. 큰 빌드 검증은 Task 19 통합 테스트 + Task 20 최종 빌드에서.

---

## Task 1: 의존성 추가

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: build.gradle.kts에 의존성 추가**

`dependencies { ... }` 블록 안에 다음을 추가한다 (기존 항목 사이 적절한 위치). 기존 `implementation("org.springframework.boot:spring-boot-starter-webmvc")` 다음 줄들에 이어 붙이면 됨.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.security:spring-security-test")
```

- [ ] **Step 2: 의존성 다운로드 확인**

Run: `./gradlew dependencies --configuration runtimeClasspath -q | grep -E "spring-security|oauth2-client|jjwt" | head -20`
Expected: `spring-security-config`, `spring-security-oauth2-client`, `jjwt-api/impl/jackson` 줄들이 출력됨.

- [ ] **Step 3: 컴파일 확인 (기존 코드 깨지지 않음)**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

> Spring Security가 들어오면서 모든 엔드포인트가 기본적으로 인증 필요 상태가 되지만, 아직 실행은 안 하므로 컴파일만 통과하면 OK.

- [ ] **Step 4: 커밋**

```bash
git add build.gradle.kts
git commit -m "chore: spring security + oauth2-client + jjwt 의존성 추가"
```

---

## Task 2: ErrorCode 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`

- [ ] **Step 1: AUTH_* 항목 5개 추가**

파일 전체를 다음으로 교체.

```kotlin
package com.team2.server.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val httpStatus: HttpStatus,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),

    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다"),
    AUTH_OAUTH_FAILURE(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다"),
    AUTH_USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다"),
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt
git commit -m "feat: 인증 관련 ErrorCode 5종 추가"
```

---

## Task 3: User 엔티티 확장 — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/user/repository/UserRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/user/entity/User.kt`
- Test: `src/test/kotlin/com/team2/server/user/UserRepositoryTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/user/UserRepositoryTest.kt`:

```kotlin
package com.team2.server.user

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
class UserRepositoryTest
    @Autowired
    constructor(
        private val userRepository: UserRepository,
    ) {
        private fun newUser(providerId: String, email: String = "$providerId@kakao.local") =
            User(
                name = "닉",
                birthDay = "01-01",
                provider = AuthProvider.KAKAO,
                providerId = providerId,
                email = email,
            )

        @Test
        fun `findByProviderAndProviderId 매칭 사용자 반환`() {
            val saved = userRepository.save(newUser("kakao-1"))

            val found = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-1")

            assertNotNull(found)
            assertEquals(saved.id, found.id)
        }

        @Test
        fun `findByProviderAndProviderId 미매칭 시 null`() {
            val found = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "missing")
            assertNull(found)
        }

        @Test
        fun `provider provider_id 복합 unique 제약 위반 시 예외`() {
            userRepository.saveAndFlush(newUser("dup", "a@kakao.local"))

            assertThrows<DataIntegrityViolationException> {
                userRepository.saveAndFlush(newUser("dup", "b@kakao.local"))
            }
        }
    }
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.team2.server.user.UserRepositoryTest" -q`
Expected: FAIL — `UserRepository` 미존재, `User`에 `providerId` 파라미터 없음.

- [ ] **Step 3: User 엔티티 수정**

`src/main/kotlin/com/team2/server/user/entity/User.kt` 전체:

```kotlin
package com.team2.server.user.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_users_provider_provider_id",
            columnNames = ["provider", "provider_id"],
        ),
    ],
)
class User(
    @Column(nullable = false)
    var name: String,
    @Column(name = "birth_day", nullable = false, length = 5)
    var birthDay: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    var provider: AuthProvider,
    @Column(name = "provider_id", nullable = false, length = 100)
    var providerId: String,
    @Column(nullable = false)
    var email: String,
) : BaseEntity()

enum class AuthProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    NAVER,
}
```

- [ ] **Step 4: UserRepository 생성**

`src/main/kotlin/com/team2/server/user/repository/UserRepository.kt`:

```kotlin
package com.team2.server.user.repository

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.team2.server.user.UserRepositoryTest" -q`
Expected: 3 tests, BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/user/entity/User.kt \
        src/main/kotlin/com/team2/server/user/repository/UserRepository.kt \
        src/test/kotlin/com/team2/server/user/UserRepositoryTest.kt
git commit -m "feat: User에 providerId 추가 및 UserRepository 작성"
```

---

## Task 4: JwtProperties

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt`
- Modify: `src/main/kotlin/com/team2/server/ServerApplication.kt`

- [ ] **Step 1: JwtProperties 생성**

`src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt`:

```kotlin
package com.team2.server.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val expirationHours: Long = 24,
)
```

- [ ] **Step 2: ServerApplication에 ConfigurationPropertiesScan 추가**

`src/main/kotlin/com/team2/server/ServerApplication.kt` 전체:

```kotlin
package com.team2.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ServerApplication

fun main(args: Array<String>) {
    runApplication<ServerApplication>(*args)
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/config/JwtProperties.kt \
        src/main/kotlin/com/team2/server/ServerApplication.kt
git commit -m "feat: JwtProperties와 ConfigurationPropertiesScan 추가"
```

---

## Task 5: JwtTokenProvider — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/jwt/JwtTokenProvider.kt`
- Test: `src/test/kotlin/com/team2/server/auth/jwt/JwtTokenProviderTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/jwt/JwtTokenProviderTest.kt`:

```kotlin
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
        val user = User(
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
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.jwt.JwtTokenProviderTest" -q`
Expected: FAIL — `JwtTokenProvider` 클래스 미존재.

- [ ] **Step 3: JwtTokenProvider 구현**

`src/main/kotlin/com/team2/server/auth/jwt/JwtTokenProvider.kt`:

```kotlin
package com.team2.server.auth.jwt

import com.team2.server.auth.config.JwtProperties
import com.team2.server.user.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val props: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret))

    fun issue(user: User): String {
        val now = Instant.now()
        val exp = now.plus(props.expirationHours, ChronoUnit.HOURS)
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("provider", user.provider.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.jwt.JwtTokenProviderTest" -q`
Expected: 3 tests, BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/jwt/JwtTokenProvider.kt \
        src/test/kotlin/com/team2/server/auth/jwt/JwtTokenProviderTest.kt
git commit -m "feat: JwtTokenProvider 구현 및 단위 테스트 추가"
```

---

## Task 6: UserPrincipal

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/principal/UserPrincipal.kt`

- [ ] **Step 1: UserPrincipal 생성**

`src/main/kotlin/com/team2/server/auth/principal/UserPrincipal.kt`:

```kotlin
package com.team2.server.auth.principal

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

data class UserPrincipal(
    val userId: Long,
    val email: String,
    val provider: AuthProvider,
    private val attrs: Map<String, Any> = emptyMap(),
    private val authoritiesSet: Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")),
) : OAuth2User, UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = authoritiesSet

    override fun getName(): String = userId.toString()

    override fun getAttributes(): Map<String, Any> = attrs

    override fun getPassword(): String? = null

    override fun getUsername(): String = userId.toString()

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    companion object {
        fun from(user: User, attrs: Map<String, Any> = emptyMap()): UserPrincipal =
            UserPrincipal(
                userId = user.id,
                email = user.email,
                provider = user.provider,
                attrs = attrs,
            )
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/principal/UserPrincipal.kt
git commit -m "feat: UserPrincipal (OAuth2User + UserDetails) 추가"
```

---

## Task 7: OAuth2Attributes 인터페이스 + KakaoAttributes — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2Attributes.kt`
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributes.kt`
- Test: `src/test/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributesTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributesTest.kt`:

```kotlin
package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KakaoAttributesTest {
    @Test
    fun `정상 응답 파싱`() {
        val raw = mapOf(
            "id" to 123456789L,
            "kakao_account" to mapOf(
                "email" to "user@kakao.com",
                "profile" to mapOf("nickname" to "홍길동"),
            ),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals(AuthProvider.KAKAO, attrs.provider)
        assertEquals("123456789", attrs.providerId)
        assertEquals("user@kakao.com", attrs.email)
        assertEquals("홍길동", attrs.nickname)
    }

    @Test
    fun `email 미동의 시 fallback`() {
        val raw = mapOf(
            "id" to 999L,
            "kakao_account" to mapOf(
                "profile" to mapOf("nickname" to "닉"),
            ),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals("999@kakao.local", attrs.email)
        assertEquals("닉", attrs.nickname)
    }

    @Test
    fun `profile 누락 시 nickname fallback`() {
        val raw = mapOf(
            "id" to 555L,
            "kakao_account" to mapOf("email" to "x@kakao.com"),
        )

        val attrs = KakaoAttributes(raw)

        assertEquals("사용자555", attrs.nickname)
        assertEquals("x@kakao.com", attrs.email)
    }

    @Test
    fun `kakao_account 누락 시 모두 fallback`() {
        val raw = mapOf<String, Any>("id" to 7L)

        val attrs = KakaoAttributes(raw)

        assertEquals("7", attrs.providerId)
        assertEquals("7@kakao.local", attrs.email)
        assertEquals("사용자7", attrs.nickname)
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.attributes.KakaoAttributesTest" -q`
Expected: FAIL — 클래스 미존재.

- [ ] **Step 3: OAuth2Attributes 인터페이스 생성**

`src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2Attributes.kt`:

```kotlin
package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider

interface OAuth2Attributes {
    val provider: AuthProvider
    val providerId: String
    val email: String
    val nickname: String
}
```

- [ ] **Step 4: KakaoAttributes 구현**

`src/main/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributes.kt`:

```kotlin
package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider

class KakaoAttributes(raw: Map<String, Any>) : OAuth2Attributes {
    override val provider: AuthProvider = AuthProvider.KAKAO
    override val providerId: String = raw["id"].toString()

    private val account: Map<String, Any> =
        @Suppress("UNCHECKED_CAST")
        (raw["kakao_account"] as? Map<String, Any>) ?: emptyMap()

    private val profile: Map<String, Any> =
        @Suppress("UNCHECKED_CAST")
        (account["profile"] as? Map<String, Any>) ?: emptyMap()

    override val email: String = (account["email"] as? String) ?: "$providerId@kakao.local"
    override val nickname: String = (profile["nickname"] as? String) ?: "사용자$providerId"
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.attributes.KakaoAttributesTest" -q`
Expected: 4 tests, BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2Attributes.kt \
        src/main/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributes.kt \
        src/test/kotlin/com/team2/server/auth/oauth2/attributes/KakaoAttributesTest.kt
git commit -m "feat: OAuth2Attributes 인터페이스와 KakaoAttributes 파서 추가"
```

---

## Task 8: OAuth2AttributesFactory — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactory.kt`
- Test: `src/test/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactoryTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactoryTest.kt`:

```kotlin
package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuth2AttributesFactoryTest {
    private val rawKakao = mapOf<String, Any>(
        "id" to 1L,
        "kakao_account" to mapOf("email" to "a@kakao.com", "profile" to mapOf("nickname" to "n")),
    )

    @Test
    fun `kakao registrationId는 KakaoAttributes 반환`() {
        val attrs = OAuth2AttributesFactory.of("kakao", rawKakao)

        assertTrue(attrs is KakaoAttributes)
        assertEquals(AuthProvider.KAKAO, attrs.provider)
        assertEquals("1", attrs.providerId)
    }

    @Test
    fun `대문자 KAKAO도 동작`() {
        val attrs = OAuth2AttributesFactory.of("KAKAO", rawKakao)
        assertEquals(AuthProvider.KAKAO, attrs.provider)
    }

    @Test
    fun `미지원 provider GOOGLE은 OAuth2AuthenticationException`() {
        assertThrows<OAuth2AuthenticationException> {
            OAuth2AttributesFactory.of("google", emptyMap())
        }
    }

    @Test
    fun `정의되지 않은 registrationId는 IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            OAuth2AttributesFactory.of("unknown", emptyMap())
        }
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.attributes.OAuth2AttributesFactoryTest" -q`
Expected: FAIL — Factory 미존재.

- [ ] **Step 3: 구현**

`src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactory.kt`:

```kotlin
package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

object OAuth2AttributesFactory {
    fun of(registrationId: String, raw: Map<String, Any>): OAuth2Attributes {
        val provider = AuthProvider.valueOf(registrationId.uppercase())
        return when (provider) {
            AuthProvider.KAKAO -> KakaoAttributes(raw)
            AuthProvider.GOOGLE,
            AuthProvider.APPLE,
            AuthProvider.NAVER,
            ->
                throw OAuth2AuthenticationException(
                    OAuth2Error("unsupported_provider", "지원하지 않는 provider: $provider", null),
                )
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.attributes.OAuth2AttributesFactoryTest" -q`
Expected: 4 tests, BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactory.kt \
        src/test/kotlin/com/team2/server/auth/oauth2/attributes/OAuth2AttributesFactoryTest.kt
git commit -m "feat: OAuth2AttributesFactory 추가 (provider 디스패치)"
```

---

## Task 9: CustomOAuth2UserService — TDD (Repository 페이크)

**Files:**
- Create: `src/test/kotlin/com/team2/server/auth/FakeUserRepository.kt`
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserService.kt`
- Test: `src/test/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserServiceTest.kt`

> **참고**: `super.loadUser()`가 카카오 API 호출을 수행하므로 통합 테스트가 어렵다. 이 Task에서는 카카오 응답 파싱 + upsert 로직을 protected 함수 `processOAuth2User`로 추출하여 단위 테스트한다.

- [ ] **Step 1: FakeUserRepository 생성 (테스트 헬퍼)**

`src/test/kotlin/com/team2/server/auth/FakeUserRepository.kt`:

```kotlin
package com.team2.server.auth

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import java.lang.reflect.Field
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

class FakeUserRepository : UserRepository {
    private val store = mutableMapOf<Long, User>()
    private val seq = AtomicLong(1L)

    override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
        store.values.firstOrNull { it.provider == provider && it.providerId == providerId }

    override fun <S : User?> save(entity: S): S {
        val user = entity!!
        if (user.id == 0L) {
            val newId = seq.getAndIncrement()
            val idField: Field = user.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(user, newId)
        }
        store[user.id] = user
        @Suppress("UNCHECKED_CAST")
        return user as S
    }

    override fun findById(id: Long): Optional<User> = Optional.ofNullable(store[id])

    override fun findAll(): MutableList<User> = store.values.toMutableList()

    override fun count(): Long = store.size.toLong()

    fun all(): List<User> = store.values.toList()

    // 미사용 메서드는 NotImplementedError
    override fun <S : User?> saveAll(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()
    override fun flush() {}
    override fun <S : User?> saveAndFlush(entity: S): S = save(entity)
    override fun <S : User?> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()
    override fun deleteAllInBatch(entities: MutableIterable<User>) {}
    override fun deleteAllByIdInBatch(ids: MutableIterable<Long>) {}
    override fun deleteAllInBatch() = store.clear()
    override fun getOne(id: Long): User = store.getValue(id)
    override fun getById(id: Long): User = store.getValue(id)
    override fun getReferenceById(id: Long): User = store.getValue(id)
    override fun existsById(id: Long): Boolean = store.containsKey(id)
    override fun findAllById(ids: MutableIterable<Long>): MutableList<User> =
        ids.mapNotNull { store[it] }.toMutableList()
    override fun findAll(sort: Sort): MutableList<User> = findAll()
    override fun findAll(pageable: Pageable): Page<User> = throw NotImplementedError()
    override fun deleteById(id: Long) { store.remove(id) }
    override fun delete(entity: User) { store.remove(entity.id) }
    override fun deleteAllById(ids: MutableIterable<Long>) { ids.forEach { store.remove(it) } }
    override fun deleteAll(entities: MutableIterable<User>) { entities.forEach { delete(it) } }
    override fun deleteAll() { store.clear() }
    override fun <S : User?> findOne(example: Example<S>): Optional<S> = throw NotImplementedError()
    override fun <S : User?> findAll(example: Example<S>): MutableList<S> = throw NotImplementedError()
    override fun <S : User?> findAll(example: Example<S>, sort: Sort): MutableList<S> = throw NotImplementedError()
    override fun <S : User?> findAll(example: Example<S>, pageable: Pageable): Page<S> = throw NotImplementedError()
    override fun <S : User?> count(example: Example<S>): Long = 0
    override fun <S : User?> exists(example: Example<S>): Boolean = false
    override fun <S : User?, R : Any?> findBy(
        example: Example<S>,
        queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>,
    ): R = throw NotImplementedError()
}
```

- [ ] **Step 2: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserServiceTest.kt`:

```kotlin
package com.team2.server.auth.oauth2

import com.team2.server.auth.FakeUserRepository
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CustomOAuth2UserServiceTest {
    private fun service(): Pair<CustomOAuth2UserService, FakeUserRepository> {
        val repo = FakeUserRepository()
        return CustomOAuth2UserService(repo) to repo
    }

    private val rawKakao = mapOf<String, Any>(
        "id" to 12345L,
        "kakao_account" to mapOf(
            "email" to "u@kakao.com",
            "profile" to mapOf("nickname" to "홍길동"),
        ),
    )

    @Test
    fun `신규 사용자는 저장되고 UserPrincipal 반환`() {
        val (svc, repo) = service()

        val principal = svc.processOAuth2User("kakao", rawKakao) as UserPrincipal

        assertEquals(1, repo.all().size)
        val saved = repo.all().first()
        assertEquals("12345", saved.providerId)
        assertEquals(AuthProvider.KAKAO, saved.provider)
        assertEquals("u@kakao.com", saved.email)
        assertEquals("홍길동", saved.name)
        assertEquals("01-01", saved.birthDay)
        assertEquals(saved.id, principal.userId)
    }

    @Test
    fun `기존 사용자는 저장 없이 반환`() {
        val (svc, repo) = service()

        svc.processOAuth2User("kakao", rawKakao)
        val countAfterFirst = repo.all().size
        val principal = svc.processOAuth2User("kakao", rawKakao) as UserPrincipal

        assertEquals(countAfterFirst, repo.all().size)
        assertEquals(repo.all().first().id, principal.userId)
    }

    @Test
    fun `email 미동의 신규는 디폴트 이메일`() {
        val (svc, repo) = service()
        val raw = mapOf<String, Any>(
            "id" to 999L,
            "kakao_account" to mapOf("profile" to mapOf("nickname" to "x")),
        )

        svc.processOAuth2User("kakao", raw)

        assertEquals("999@kakao.local", repo.all().first().email)
    }
}
```

- [ ] **Step 3: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.CustomOAuth2UserServiceTest" -q`
Expected: FAIL — 클래스 미존재.

- [ ] **Step 4: CustomOAuth2UserService 구현**

`src/main/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserService.kt`:

```kotlin
package com.team2.server.auth.oauth2

import com.team2.server.auth.oauth2.attributes.OAuth2AttributesFactory
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val DEFAULT_BIRTH_DAY = "01-01"

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {
    @Transactional
    override fun loadUser(req: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(req)
        return processOAuth2User(req.clientRegistration.registrationId, oauth2User.attributes)
    }

    /**
     * 카카오 API 호출 결과(attributes)를 받아 사용자를 upsert 하고 UserPrincipal 을 만든다.
     * super.loadUser() 를 우회하여 단위 테스트가 가능하도록 분리.
     */
    fun processOAuth2User(registrationId: String, attributes: Map<String, Any>): OAuth2User {
        val attrs = OAuth2AttributesFactory.of(registrationId, attributes)

        val user = userRepository.findByProviderAndProviderId(attrs.provider, attrs.providerId)
            ?: userRepository.save(
                User(
                    name = attrs.nickname,
                    birthDay = DEFAULT_BIRTH_DAY,
                    provider = attrs.provider,
                    providerId = attrs.providerId,
                    email = attrs.email,
                ),
            )

        return UserPrincipal.from(user, attributes)
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.CustomOAuth2UserServiceTest" -q`
Expected: 3 tests, BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserService.kt \
        src/test/kotlin/com/team2/server/auth/FakeUserRepository.kt \
        src/test/kotlin/com/team2/server/auth/oauth2/CustomOAuth2UserServiceTest.kt
git commit -m "feat: CustomOAuth2UserService 구현 (provider-agnostic upsert)"
```

---

## Task 10: JwtAuthenticationEntryPoint

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationEntryPoint.kt`

- [ ] **Step 1: 구현**

`src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationEntryPoint.kt`:

```kotlin
package com.team2.server.auth.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

const val AUTH_ERROR_REQUEST_ATTRIBUTE = "authErrorCode"

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val errorCode = (request.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE) as? ErrorCode)
            ?: ErrorCode.AUTH_UNAUTHORIZED
        val body = ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message)

        response.status = errorCode.httpStatus.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, body)
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationEntryPoint.kt
git commit -m "feat: JwtAuthenticationEntryPoint 추가 (ApiResponse 포맷 401)"
```

---

## Task 11: JwtAuthenticationFilter — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilter.kt`
- Test: `src/test/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilterTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilterTest.kt`:

```kotlin
package com.team2.server.auth.jwt

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.lang.reflect.Field
import java.util.Base64
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val tokenProvider = JwtTokenProvider(JwtProperties(secret = secret, expirationHours = 24))

    private fun userWithId(id: Long): User {
        val user = User(
            name = "n",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "p$id",
            email = "$id@kakao.local",
        )
        val idField: Field = user.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
        return user
    }

    private fun newFilter(repo: UserRepository): JwtAuthenticationFilter =
        JwtAuthenticationFilter(tokenProvider, repo)

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `유효한 Bearer 토큰은 SecurityContext에 인증 세팅`() {
        val user = userWithId(7L)
        val repo = mock<UserRepository>()
        whenever(repo.findById(7L)).thenReturn(Optional.of(user))
        val token = tokenProvider.issue(user)

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals(7L, (auth.principal as UserPrincipal).userId)
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }

    @Test
    fun `만료 토큰은 컨텍스트 미설정 + 요청 속성에 EXPIRED 코드`() {
        val user = userWithId(1L)
        val expiredProvider = JwtTokenProvider(JwtProperties(secret = secret, expirationHours = 0))
        val repo = mock<UserRepository>()
        val expired = expiredProvider.issue(user)
        Thread.sleep(50)

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $expired") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_EXPIRED_TOKEN, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }

    @Test
    fun `잘못된 시그니처는 INVALID_TOKEN 속성`() {
        val other = JwtTokenProvider(
            JwtProperties(
                secret = Base64.getEncoder().encodeToString(ByteArray(64) { (it + 1).toByte() }),
                expirationHours = 24,
            ),
        )
        val token = other.issue(userWithId(2L))
        val repo = mock<UserRepository>()

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_INVALID_TOKEN, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
    }

    @Test
    fun `userId DB 미존재면 USER_NOT_FOUND 속성`() {
        val token = tokenProvider.issue(userWithId(99L))
        val repo = mock<UserRepository>()
        whenever(repo.findById(99L)).thenReturn(Optional.empty())

        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
    }

    @Test
    fun `Authorization 헤더 없으면 통과 + 컨텍스트 미설정`() {
        val repo = mock<UserRepository>()
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        newFilter(repo).doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNull(req.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE))
        verify(chain).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
    }
}
```

> mockito-kotlin이 spring-boot-starter-test에 포함되어 있지 않으므로 build.gradle에 추가해야 한다. 다음 단계에서 추가.

- [ ] **Step 2: mockito-kotlin 의존성 추가**

`build.gradle.kts`의 `dependencies { ... }` 끝에 추가:

```kotlin
testImplementation("org.mockito.kotlin:mockito-kotlin:5.5.0")
```

- [ ] **Step 3: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.jwt.JwtAuthenticationFilterTest" -q`
Expected: FAIL — `JwtAuthenticationFilter` 미존재.

- [ ] **Step 4: JwtAuthenticationFilter 구현**

`src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilter.kt`:

```kotlin
package com.team2.server.auth.jwt

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.repository.UserRepository
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val BEARER_PREFIX = "Bearer "

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            authenticate(token, request)
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX)) header.removePrefix(BEARER_PREFIX).trim() else null
    }

    private fun authenticate(token: String, request: HttpServletRequest) {
        try {
            val claims = jwtTokenProvider.parse(token)
            val userId = claims.subject.toLong()
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_USER_NOT_FOUND)
                return
            }
            val principal = UserPrincipal.from(user)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        } catch (e: ExpiredJwtException) {
            log.debug("Expired JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_EXPIRED_TOKEN)
        } catch (e: JwtException) {
            log.debug("Invalid JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN)
        } catch (e: IllegalArgumentException) {
            log.debug("Malformed JWT", e)
            request.setAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN)
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.jwt.JwtAuthenticationFilterTest" -q`
Expected: 5 tests, BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add build.gradle.kts \
        src/main/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilter.kt \
        src/test/kotlin/com/team2/server/auth/jwt/JwtAuthenticationFilterTest.kt
git commit -m "feat: JwtAuthenticationFilter 구현 및 mockito-kotlin 의존성 추가"
```

---

## Task 12: OAuth2SuccessHandler — TDD

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandler.kt`
- Test: `src/test/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandlerTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandlerTest.kt`:

```kotlin
package com.team2.server.auth.oauth2

import com.team2.server.auth.FakeUserRepository
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import java.lang.reflect.Field
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2SuccessHandlerTest {
    private val secret = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
    private val tokenProvider = JwtTokenProvider(JwtProperties(secret = secret))

    private fun seedUser(repo: FakeUserRepository, id: Long): User {
        val u = User(
            name = "n",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "p$id",
            email = "$id@kakao.local",
        )
        val idField: Field = u.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(u, id)
        repo.save(u)
        return u
    }

    private fun handler(
        repo: FakeUserRepository,
        allowed: List<String> = listOf("http://localhost:3000/oauth/redirect"),
    ): OAuth2SuccessHandler = OAuth2SuccessHandler(tokenProvider, repo, allowed)

    private fun authWith(principal: UserPrincipal): Authentication {
        val auth = mock<Authentication>()
        whenever(auth.principal).thenReturn(principal)
        return auth
    }

    @Test
    fun `허용 redirect_uri 쿼리에 토큰 포함하여 302`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 10L)
        val req = MockHttpServletRequest().apply { setParameter("redirect_uri", "http://localhost:3000/oauth/redirect") }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo).onAuthenticationSuccess(req, res, auth)

        assertEquals(302, res.status)
        val location = res.getHeader("Location")
        assertNotNull(location)
        assertTrue(location.startsWith("http://localhost:3000/oauth/redirect?token="))
    }

    @Test
    fun `redirect_uri 미지정 시 디폴트(첫번째 허용목록) 사용`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 11L)
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("https://app.example.com/cb", "https://other.example.com/cb"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?token="))
    }

    @Test
    fun `허용 목록에 없는 redirect_uri는 디폴트로 폴백`() {
        val repo = FakeUserRepository()
        val user = seedUser(repo, 12L)
        val req = MockHttpServletRequest().apply { setParameter("redirect_uri", "https://evil.example.com/steal") }
        val res = MockHttpServletResponse()
        val auth = authWith(UserPrincipal.from(user))

        handler(repo, allowed = listOf("https://app.example.com/cb"))
            .onAuthenticationSuccess(req, res, auth)

        val location = res.getHeader("Location") ?: error("no location")
        assertTrue(location.startsWith("https://app.example.com/cb?token="))
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.OAuth2SuccessHandlerTest" -q`
Expected: FAIL — `OAuth2SuccessHandler` 미존재.

- [ ] **Step 3: 구현**

`src/main/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandler.kt`:

```kotlin
package com.team2.server.auth.oauth2

import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.user.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2SuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
    @Value("\${app.oauth2.authorized-redirect-uris}")
    private val allowedRedirectUris: List<String>,
) : SimpleUrlAuthenticationSuccessHandler() {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as UserPrincipal
        val user = userRepository.findById(principal.userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        val token = jwtTokenProvider.issue(user)

        val target = resolveRedirectUri(request) ?: allowedRedirectUris.first()
        val redirectUrl = UriComponentsBuilder.fromUriString(target)
            .queryParam("token", token)
            .build()
            .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun resolveRedirectUri(request: HttpServletRequest): String? {
        val candidate = request.getParameter("redirect_uri") ?: return null
        return if (allowedRedirectUris.contains(candidate)) candidate else null
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.team2.server.auth.oauth2.OAuth2SuccessHandlerTest" -q`
Expected: 3 tests, BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandler.kt \
        src/test/kotlin/com/team2/server/auth/oauth2/OAuth2SuccessHandlerTest.kt
git commit -m "feat: OAuth2SuccessHandler 추가 (JWT 발급 + redirect 화이트리스트)"
```

---

## Task 13: OAuth2FailureHandler

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/oauth2/OAuth2FailureHandler.kt`

- [ ] **Step 1: 구현**

`src/main/kotlin/com/team2/server/auth/oauth2/OAuth2FailureHandler.kt`:

```kotlin
package com.team2.server.auth.oauth2

import com.team2.server.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2FailureHandler(
    @Value("\${app.oauth2.authorized-redirect-uris}")
    private val allowedRedirectUris: List<String>,
) : SimpleUrlAuthenticationFailureHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        log.warn("OAuth2 authentication failed", exception)

        val target = allowedRedirectUris.first()
        val redirectUrl = UriComponentsBuilder.fromUriString(target)
            .queryParam("error", ErrorCode.AUTH_OAUTH_FAILURE.name)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/oauth2/OAuth2FailureHandler.kt
git commit -m "feat: OAuth2FailureHandler 추가 (실패 redirect)"
```

---

## Task 14: SecurityConfig

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`

- [ ] **Step 1: 구현**

`src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`:

```kotlin
package com.team2.server.auth.config

import com.team2.server.auth.jwt.JwtAuthenticationEntryPoint
import com.team2.server.auth.jwt.JwtAuthenticationFilter
import com.team2.server.auth.oauth2.CustomOAuth2UserService
import com.team2.server.auth.oauth2.OAuth2FailureHandler
import com.team2.server.auth.oauth2.OAuth2SuccessHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val oAuth2FailureHandler: OAuth2FailureHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    @Value("\${app.oauth2.authorized-redirect-uris}")
    private val allowedRedirectUris: List<String>,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/oauth2/**",
                    "/login/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                ).permitAll()
                auth.anyRequest().authenticated()
            }
            .oauth2Login { oauth ->
                oauth.userInfoEndpoint { it.userService(customOAuth2UserService) }
                oauth.successHandler(oAuth2SuccessHandler)
                oauth.failureHandler(oAuth2FailureHandler)
            }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedRedirectUris
            .mapNotNull { runCatching { java.net.URI(it) }.getOrNull() }
            .map { "${it.scheme}://${it.authority}" }
            .distinct()
        val config = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
git commit -m "feat: SecurityConfig 추가 (oauth2Login + JWT 필터 + CORS)"
```

---

## Task 15: AuthController & UserResponse

**Files:**
- Create: `src/main/kotlin/com/team2/server/auth/controller/UserResponse.kt`
- Create: `src/main/kotlin/com/team2/server/auth/controller/AuthController.kt`

- [ ] **Step 1: UserResponse DTO 생성**

`src/main/kotlin/com/team2/server/auth/controller/UserResponse.kt`:

```kotlin
package com.team2.server.auth.controller

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val provider: AuthProvider,
    val birthDay: String,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                name = user.name,
                email = user.email,
                provider = user.provider,
                birthDay = user.birthDay,
            )
    }
}
```

- [ ] **Step 2: AuthController 생성**

`src/main/kotlin/com/team2/server/auth/controller/AuthController.kt`:

```kotlin
package com.team2.server.auth.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ApiResponse
import com.team2.server.user.repository.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<UserResponse> {
        val user = userRepository.findById(principal.userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return ApiResponse.success(UserResponse.from(user))
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/auth/controller/UserResponse.kt \
        src/main/kotlin/com/team2/server/auth/controller/AuthController.kt
git commit -m "feat: AuthController /api/auth/me 추가"
```

---

## Task 16: 테스트 환경 yml 보강

**Files:**
- Modify: `src/test/resources/application.yml`

> 테스트는 secret 서브모듈을 로드하지 않으므로 테스트용 OAuth client 더미와 JWT 시크릿을 직접 명시한다.

- [ ] **Step 1: application.yml 수정**

`src/test/resources/application.yml` 전체:

```yaml
spring:
  application:
    name: team2-backend-test
  profiles:
    active: test
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: test-kakao-client-id
            client-secret: test-kakao-client-secret
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: [profile_nickname, account_email]
            client-name: Kakao
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id

app:
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LXdpdGgtYXQtbGVhc3QtMjU2LWJpdHMtZm9yLWhtYWMtc2hhMjU2LWFsZ29yaXRobS0xMjM0NTY3OA==
    expiration-hours: 24
  oauth2:
    authorized-redirect-uris:
      - http://localhost:3000/oauth/redirect
```

> `app.jwt.secret`은 base64로 인코딩된 64바이트 더미. 테스트 전용.

- [ ] **Step 2: 기존 테스트가 깨지지 않는지 확인**

Run: `./gradlew test --tests "com.team2.server.ServerApplicationTests" -q`
Expected: PASS (contextLoads).

- [ ] **Step 3: 커밋**

```bash
git add src/test/resources/application.yml
git commit -m "chore: 테스트용 OAuth client 및 JWT 설정 추가"
```

---

## Task 17: 통합 테스트 — SecurityFilterChain end-to-end

**Files:**
- Create: `src/test/kotlin/com/team2/server/auth/SecurityIntegrationTest.kt`

- [ ] **Step 1: 통합 테스트 작성**

`src/test/kotlin/com/team2/server/auth/SecurityIntegrationTest.kt`:

```kotlin
package com.team2.server.auth

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @Test
        fun `actuator health 는 인증 없이 접근 가능`() {
            mockMvc.get("/actuator/health").andExpect {
                status { isOk() }
            }
        }

        @Test
        fun `api auth me 는 토큰 없이 401`() {
            mockMvc.get("/api/auth/me").andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
            }
        }

        @Test
        fun `api auth me 는 유효한 토큰으로 200`() {
            val user = userRepository.save(
                User(
                    name = "n",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = "kakao-int-1",
                    email = "int@kakao.local",
                ),
            )
            val token = tokenProvider.issue(user)

            mockMvc.get("/api/auth/me") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(user.id) }
                jsonPath("$.data.email") { value("int@kakao.local") }
                jsonPath("$.data.provider") { value("KAKAO") }
            }
        }

        @Test
        fun `잘못된 토큰은 INVALID_TOKEN`() {
            mockMvc.get("/api/auth/me") {
                header("Authorization", "Bearer not-a-jwt")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
            }
        }

        @Test
        fun `oauth2 authorization kakao 는 카카오로 302 리다이렉트`() {
            mockMvc.get("/oauth2/authorization/kakao").andExpect {
                status { is3xxRedirection() }
                header { stringValues("Location", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("kauth.kakao.com"))) }
            }
        }
    }
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew test --tests "com.team2.server.auth.SecurityIntegrationTest" -q`
Expected: 5 tests, BUILD SUCCESSFUL.

> 실패 케이스 트러블슈팅:
> - `AUTH_UNAUTHORIZED` 응답이 `AUTH_INVALID_TOKEN`으로 나오면 EntryPoint가 request attribute를 못 읽는 것 — `JwtAuthenticationEntryPoint` 검토.
> - `oauth2/authorization/kakao` 가 200을 반환하면 `oauth2Login`이 비활성 — `SecurityConfig` 검토.

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/com/team2/server/auth/SecurityIntegrationTest.kt
git commit -m "test: SecurityFilterChain end-to-end 통합 테스트"
```

---

## Task 18: 전체 빌드 + 정적 분석

**Files:** 없음 (검증 단계)

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: ktlint 검사**

Run: `./gradlew ktlintCheck -q`
Expected: BUILD SUCCESSFUL.

> 실패 시 `./gradlew ktlintFormat`으로 자동 수정 후 변경 분 확인.

- [ ] **Step 3: detekt 검사**

Run: `./gradlew detekt -q`
Expected: BUILD SUCCESSFUL (또는 기존 워닝과 동일 수준).

- [ ] **Step 4: jacoco 리포트 생성 및 확인**

Run: `./gradlew jacocoTestReport -q`
파일: `build/reports/jacoco/test/html/index.html` 열어서 `auth` 패키지 라인 커버리지 80%+ 확인.

- [ ] **Step 5: 전체 빌드**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 만약 ktlint/detekt 자동수정이 발생했다면 커밋**

```bash
git add -u
git commit -m "chore: ktlint/detekt 자동 포맷 적용"
```

> 변경 없으면 이 단계 스킵.

---

## Task 19: 시크릿 서브모듈 업데이트 가이드 (별도 PR/푸시)

**Files:** (서브모듈 외부)
- `config/secret/application-secret.yml`
- `config/secret/application-secret-dev.yml`
- `config/secret/application-secret-prod.yml`

> 이 작업은 **별도 git 저장소(서브모듈)에서 수행**한다. 본 저장소 PR 머지와 별개로 시크릿이 배포돼야 dev/prod에서 실행 가능하다. 본 PR 본문에 이 가이드를 링크한다.

- [ ] **Step 1: JWT 시크릿 생성**

Run: `openssl rand -base64 64`
출력값을 복사해 둔다. dev/prod 각각 다르게 생성한다.

- [ ] **Step 2: 카카오 디벨로퍼 콘솔 작업**

- developers.kakao.com 로그인 → 내 애플리케이션 → 앱 추가 (dev / prod 각각).
- 각 앱에서 **앱 키 → REST API 키** 복사 (= `client-id`).
- 보안 → Client Secret 활성화 (선택). 활성화한 키 복사 (= `client-secret`).
- 카카오 로그인 → 활성화 ON.
- Redirect URI 등록:
  - dev: `https://<dev-도메인>/login/oauth2/code/kakao`
  - prod: `https://<prod-도메인>/login/oauth2/code/kakao`
  - 로컬 테스트: `http://localhost:8080/login/oauth2/code/kakao`
- 동의 항목 → `프로필 정보(닉네임)` 필수, `카카오계정(이메일)` 선택 동의.

- [ ] **Step 3: 서브모듈 yml 작성**

`config/secret/application-secret.yml` 끝에 추가 (기존 datasource 블록 유지):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: <카카오 REST API 키>
            client-secret: <카카오 시크릿 — 미사용 시 생략>
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: [profile_nickname, account_email]
            client-name: Kakao
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id

app:
  jwt:
    secret: <openssl rand -base64 64 결과>
    expiration-hours: 24
  oauth2:
    authorized-redirect-uris:
      - http://localhost:3000/oauth/redirect
```

`config/secret/application-secret-dev.yml`에 환경별 오버라이드 추가:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: <DEV 카카오 키>
            client-secret: <DEV 카카오 시크릿>
app:
  jwt:
    secret: <DEV JWT 시크릿>
  oauth2:
    authorized-redirect-uris:
      - https://<dev-프론트>/oauth/redirect
```

`config/secret/application-secret-prod.yml`에도 동일 패턴으로 PROD 값 추가.

- [ ] **Step 4: 서브모듈 푸시**

```bash
cd config/secret
git checkout -b feature/kakao-oauth-secrets
git add application-secret.yml application-secret-dev.yml application-secret-prod.yml
git commit -m "feat: 카카오 OAuth + JWT 시크릿 추가"
git push -u origin feature/kakao-oauth-secrets
# 서브모듈 저장소에서 PR 머지 (별도 권한)
cd ../..
```

서브모듈 머지 후 본 저장소에서:

```bash
cd config/secret && git checkout main && git pull && cd ../..
git add config/secret
git commit -m "chore: secret 서브모듈 OAuth/JWT 설정 반영"
```

> 본 PR에 이 커밋을 포함할지는 시크릿 PR 머지 타이밍에 따라 결정. 별도 후속 PR로 처리해도 무방.

---

## Task 20: prod DDL 마이그레이션 메모 (후속 PR 안내)

**Files:**
- Create: `docs/superpowers/specs/2026-04-26-kakao-oauth-prod-migration.md`

- [ ] **Step 1: 마이그레이션 메모 작성**

`docs/superpowers/specs/2026-04-26-kakao-oauth-prod-migration.md`:

```markdown
# 카카오 OAuth 로그인 — Prod DDL 마이그레이션 메모

prod 환경(`ddl-auto: validate`)에서는 `users` 테이블에 자동 변경이 적용되지 않는다. 운영 배포 전 다음 DDL을 수동(또는 마이그레이션 도구) 실행한다.

## 변경 사항

1. `provider_id` 컬럼 추가 (`VARCHAR(100) NOT NULL`).
2. `(provider, provider_id)` 복합 unique 제약 추가.

## 기존 데이터 처리

기존 `users` 행이 있을 경우 `provider_id` 컬럼에 NOT NULL 추가는 즉시 실패한다. 다음 순서로 처리:

```sql
-- 1) nullable 컬럼 추가
ALTER TABLE users ADD COLUMN provider_id VARCHAR(100) NULL;

-- 2) 기존 데이터 백필 (provider별로 별도 작업, 신규 서비스라면 TRUNCATE 가능)
UPDATE users SET provider_id = CAST(id AS CHAR) WHERE provider_id IS NULL;

-- 3) NOT NULL 적용
ALTER TABLE users MODIFY COLUMN provider_id VARCHAR(100) NOT NULL;

-- 4) 복합 unique 제약
ALTER TABLE users ADD CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id);
```

## 후속 PR 작업

- Flyway/Liquibase 도입 여부 결정 → 도입 시 본 SQL을 마이그레이션 파일로 변환.
- 운영 배포 직전 DBA와 협의해 적용 시점 결정.
```

- [ ] **Step 2: 커밋**

```bash
git add docs/superpowers/specs/2026-04-26-kakao-oauth-prod-migration.md
git commit -m "docs: prod DDL 마이그레이션 메모 추가"
```

---

## Self-Review

### Spec coverage

| Spec 섹션 | 구현 Task |
|---|---|
| 3. 의존성 | Task 1 |
| 4. 시크릿 yml | Task 19 (서브모듈), Task 16 (test) |
| 5. JWT 클레임/Provider | Task 4, 5 |
| 6. SecurityConfig | Task 14 |
| 7. User 엔티티 변경 | Task 3 |
| 8. OAuth2 사용자 처리 (확장) | Task 7, 8, 9 |
| 9. Success/Failure 핸들러 | Task 12, 13 |
| 10. AuthController | Task 15 |
| 11. ErrorCode | Task 2 |
| 12. 테스트 전략 | Task 3, 5, 7, 8, 9, 11, 12, 17 |
| 13. 마이그레이션 | Task 20 |
| 14. 비범위 | (구현 안 함) |
| 15. 보안 (open redirect, 시크릿) | Task 12 (화이트리스트), Task 19 |

`UserPrincipal`은 spec엔 명시 없지만 SecurityConfig 동작에 필수 → Task 6.

### Placeholder scan

- "TBD/TODO" 없음.
- 모든 코드 블록은 완전한 코드.
- 모든 명령어 구체적 (`./gradlew test --tests "..."`).
- 시크릿 자리표시자(`<카카오 REST API 키>` 등)는 의도된 외부 입력값.

### Type/메서드 일관성

- `JwtTokenProvider.issue(user)` / `parse(token)` 일관됨 (Task 5, 11, 12, 17).
- `UserPrincipal.from(user)` / `from(user, attrs)` 두 호출 모두 Task 6의 companion에 정의됨.
- `OAuth2AttributesFactory.of(registrationId, raw)` 시그니처 일관됨 (Task 8, 9).
- `AUTH_ERROR_REQUEST_ATTRIBUTE` 상수: Task 10에서 정의, Task 11에서 사용 — 일관됨.
- `ErrorCode.AUTH_*` 5종: Task 2 정의, Task 10/11/12/13/17에서 참조 — 일관됨.
- `app.oauth2.authorized-redirect-uris`: Task 12, 13, 14, 16, 19에서 일관 사용.
- `User.providerId`: Task 3에서 추가, 이후 모든 코드 참조 일관.

### 잠재 이슈 / 트러블슈팅 노트

- **`spring-boot-starter-test`는 mockito-core를 가져오지만 mockito-kotlin은 별도** → Task 11 Step 2에서 추가.
- **Spring Security 6의 deprecation**: `formLogin().disable()` 람다 형식 사용 (Task 14).
- **OAuth2 client redirect-uri "{baseUrl}"**: Spring이 자동 치환. test에서도 동일.
- **`super.loadUser()` 단위 테스트 어려움**: `processOAuth2User()` public 헬퍼로 분리하여 우회 (Task 9).
- **`@DataJpaTest`는 H2를 자동 구성** → 별도 datasource 설정 불필요.
- **`SecurityIntegrationTest`에서 `oauth2Login`이 작동하려면 OAuth client registration이 등록되어야 함** → Task 16의 test yml에 더미 등록.
