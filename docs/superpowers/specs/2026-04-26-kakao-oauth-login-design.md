# 카카오 OAuth 로그인 설계서

- 작성일: 2026-04-26
- 이슈: [#30 [Feat] 카카오 OAuth 로그인 기능 구현](https://github.com/depromeet/18th-team2-server/issues/30)
- 브랜치: `feature/kakao-oauth-login`

## 1. 개요

웹 클라이언트를 위한 카카오 OAuth 2.0 로그인 기능. Spring Security의 `oauth2-client` 표준 흐름을 사용하여 서버에서 인가 코드 → 토큰 → 사용자 정보 교환을 모두 수행한다. 인증 성공 시 자체 발급 JWT(24시간)를 응답하여 stateless API 인증을 구성한다.

### 결정된 사항

| 항목 | 결정 |
|---|---|
| 클라이언트 환경 | 웹 브라우저 (서버 redirect 플로우) |
| 인증 토큰 | JWT Access Token only (24시간 만료, refresh 없음) |
| 신규 가입 | 자동 가입, 누락 정보는 디폴트값 주입 |
| 식별 키 | `(provider, providerId)` 복합 unique |
| 구현 방식 | `spring-boot-starter-oauth2-client` 표준 흐름 |

## 2. 아키텍처

### 패키지 구조

```
com.team2.server.auth/
├── config/
│   ├── SecurityConfig.kt              # SecurityFilterChain
│   └── JwtProperties.kt               # @ConfigurationProperties("app.jwt")
├── jwt/
│   ├── JwtTokenProvider.kt            # JWT 생성/파싱/검증
│   ├── JwtAuthenticationFilter.kt     # OncePerRequestFilter
│   └── JwtAuthenticationEntryPoint.kt # 401 응답 (ApiResponse 포맷)
├── oauth2/
│   ├── CustomOAuth2UserService.kt     # extends DefaultOAuth2UserService
│   ├── OAuth2SuccessHandler.kt        # JWT 발급 + redirect
│   ├── OAuth2FailureHandler.kt        # 에러 redirect
│   └── attributes/
│       ├── OAuth2Attributes.kt        # provider 공통 인터페이스 (sealed)
│       ├── OAuth2AttributesFactory.kt # registrationId → OAuth2Attributes 디스패치
│       └── KakaoAttributes.kt         # KAKAO 구현
├── principal/
│   └── UserPrincipal.kt               # OAuth2User + UserDetails
└── controller/
    └── AuthController.kt              # /api/auth/me
```

### 인증 플로우

1. 클라이언트 → `GET /oauth2/authorization/kakao`
2. Spring Security가 카카오 인가 페이지로 302
3. 사용자 동의 → 카카오 → `GET /login/oauth2/code/kakao?code=...`
4. Spring Security가 토큰/유저정보 자동 교환 → `CustomOAuth2UserService.loadUser()` 실행
5. `loadUser()` 안에서 User upsert + `UserPrincipal` 반환
6. 인증 성공 → `OAuth2SuccessHandler` → JWT 발급 → 화이트리스트 redirect URI(`?token=<jwt>`)로 302
7. 이후 모든 API: `Authorization: Bearer <jwt>` → `JwtAuthenticationFilter` 검증 → `SecurityContext`에 `UserPrincipal` 세팅

## 3. 의존성

`build.gradle.kts`에 추가:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.security:spring-security-test")
```

## 4. 설정 (시크릿 주입)

이 프로젝트는 `config/secret` git 서브모듈에 환경별 yml을 두고, `application.yml`의 `spring.profiles.include: secret`으로 항상 로드하는 패턴을 사용한다. 이 패턴 그대로 따른다.

### `config/secret/application-secret.yml` (공통, 항상 로드)

```yaml
spring:
  datasource: # 기존 유지
    url: jdbc:mysql://localhost:3306/team2-local-db
    username: team2
    password: team2
    driver-class-name: com.mysql.cj.jdbc.Driver
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: <카카오 REST API 키>
            client-secret: <카카오 시크릿 — 선택>
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

### `config/secret/application-secret-dev.yml` / `application-secret-prod.yml`

환경별 카카오 앱 키 / JWT 시크릿 / redirect URI 오버라이드.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          kakao:
            client-id: <환경별 카카오 키>
            client-secret: <환경별 시크릿>
app:
  jwt:
    secret: <환경별 JWT 시크릿>
  oauth2:
    authorized-redirect-uris:
      - <환경별 프론트 URI>
```

### 카카오 디벨로퍼 콘솔 사전 작업

- dev/prod 앱 각각 Redirect URI 등록: `https://<도메인>/login/oauth2/code/kakao`
- 동의 항목: `profile_nickname` (필수), `account_email` (선택 가능, 비즈앱 검수 필요할 수 있음)

## 5. JWT

### 클레임 설계

```
Header  : { alg: HS256, typ: JWT }
Payload : {
  sub: <userId(Long) as string>,
  email: <user.email>,
  provider: "KAKAO",
  iat: <epoch sec>,
  exp: <iat + 24h>
}
```

- `sub`은 내부 User PK. `provider/providerId` 아님.
- `birthDay`, `name` 등 변경 가능한 정보는 토큰에 포함하지 않음.

### `JwtProperties`

```kotlin
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val expirationHours: Long = 24,
)
```

`ServerApplication`에 `@ConfigurationPropertiesScan` 추가.

### `JwtTokenProvider`

jjwt 0.12.x API 사용. `Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret))`로 HS256 키 구성. `issue(user: User): String`, `parse(token: String): Claims`.

### `JwtAuthenticationFilter`

`OncePerRequestFilter` 확장. `Authorization: Bearer ...` 헤더 파싱 → 토큰 검증 → DB에서 User 조회 → `UsernamePasswordAuthenticationToken`으로 SecurityContext에 세팅. 실패 시 컨텍스트 비우고 EntryPoint에 위임.

### `JwtAuthenticationEntryPoint`

`AuthenticationEntryPoint` 구현. `ApiResponse` 포맷으로 401 + `ErrorCode` 응답.

## 6. SecurityConfig

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(...) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/oauth2/**", "/login/**",
                    "/actuator/health", "/actuator/info",
                    "/swagger-ui/**", "/v3/api-docs/**",
                ).permitAll()
                it.anyRequest().authenticated()
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
}
```

CORS는 `app.oauth2.authorized-redirect-uris`의 origin들을 화이트리스트.

## 7. User 엔티티 변경

```kotlin
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_provider_provider_id", columnNames = ["provider", "provider_id"])],
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
```

신규 필드: `providerId`. 신규 unique 제약: `(provider, provider_id)`.

### 디폴트값 정책 (자동 가입 누락 정보)

- `birthDay`: `"01-01"`
- `email`: 카카오 미동의 시 `"<providerId>@kakao.local"`
- `name`: 닉네임 누락 시 `"사용자<providerId>"`

### Repository

```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
```

## 8. OAuth2 사용자 처리 (provider 확장 가능 구조)

### 확장 전략

이번 PR은 KAKAO만 구현하지만 GOOGLE/APPLE/NAVER 추가를 쉽게 하기 위해 **provider별 응답 파싱을 전략 패턴으로 분리**한다. `CustomOAuth2UserService`는 provider에 무관한 공통 흐름만 담당하고, provider 응답 구조 차이는 `OAuth2Attributes` 구현체가 흡수한다.

새 provider 추가 시 변경 범위:
1. `AuthProvider` enum에 항목 추가 (이미 GOOGLE/APPLE/NAVER 존재)
2. `OAuth2Attributes` 구현체 1개 추가 (예: `GoogleAttributes`)
3. `OAuth2AttributesFactory.of(provider, attrs)` 분기에 한 줄 추가
4. `application-secret.yml`에 `spring.security.oauth2.client.registration.<id>` 등록
5. `SecurityConfig`/필터/핸들러/JwtTokenProvider/Repository는 **수정 불필요**

### 공통 인터페이스

```kotlin
interface OAuth2Attributes {
    val provider: AuthProvider
    val providerId: String
    val email: String
    val nickname: String
}
```

### `KakaoAttributes`

```kotlin
class KakaoAttributes(raw: Map<String, Any>) : OAuth2Attributes {
    override val provider = AuthProvider.KAKAO
    override val providerId: String = raw["id"].toString()
    private val account: Map<String, Any> = raw["kakao_account"] as Map<String, Any>? ?: emptyMap()
    private val profile: Map<String, Any> = account["profile"] as Map<String, Any>? ?: emptyMap()
    override val email: String = account["email"] as String? ?: "$providerId@kakao.local"
    override val nickname: String = profile["nickname"] as String? ?: "사용자$providerId"
}
```

### `OAuth2AttributesFactory`

```kotlin
object OAuth2AttributesFactory {
    fun of(registrationId: String, raw: Map<String, Any>): OAuth2Attributes {
        val provider = AuthProvider.valueOf(registrationId.uppercase())
        return when (provider) {
            AuthProvider.KAKAO -> KakaoAttributes(raw)
            AuthProvider.GOOGLE,
            AuthProvider.APPLE,
            AuthProvider.NAVER ->
                throw OAuth2AuthenticationException(OAuth2Error("unsupported_provider", "지원하지 않는 provider: $provider", null))
        }
    }
}
```

> `when`은 sealed-like exhaustive. 새 provider 추가 시 컴파일러가 분기 누락을 잡지는 못하지만(enum이라 `else` 없는 경우 워닝), `else`를 두지 않고 명시적 분기로 작성해 누락 시 미지원 예외가 명확히 발생하게 한다.

### `CustomOAuth2UserService`

```kotlin
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {
    @Transactional
    override fun loadUser(req: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(req)
        val attrs = OAuth2AttributesFactory.of(req.clientRegistration.registrationId, oauth2User.attributes)

        val user = userRepository.findByProviderAndProviderId(attrs.provider, attrs.providerId)
            ?: userRepository.save(
                User(
                    name = attrs.nickname,
                    birthDay = "01-01",
                    provider = attrs.provider,
                    providerId = attrs.providerId,
                    email = attrs.email,
                )
            )

        return UserPrincipal.from(user, oauth2User.attributes)
    }
}
```

이 클래스는 provider-agnostic 하다. 새 provider 추가 시 본 클래스는 수정하지 않는다.

### `UserPrincipal`

`OAuth2User` + `UserDetails` 둘 다 구현하여 OAuth2 단계와 JWT 인증 단계에서 동일 객체 사용.

```kotlin
data class UserPrincipal(
    val userId: Long,
    val email: String,
    val provider: AuthProvider,
    private val attrs: Map<String, Any> = emptyMap(),
    private val authoritiesSet: Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")),
) : OAuth2User, UserDetails {
    override fun getAuthorities() = authoritiesSet
    override fun getName() = userId.toString()
    override fun getAttributes(): Map<String, Any> = attrs
    // UserDetails: password = null, username = userId.toString(), enabled = true 등
}
```

## 9. Success / Failure 핸들러

### `OAuth2SuccessHandler`

`SimpleUrlAuthenticationSuccessHandler` 확장. JWT 발급 → 화이트리스트(`app.oauth2.authorized-redirect-uris`)에 있는 URI에만 `?token=<jwt>` 붙여서 302. 화이트리스트에 없는 redirect_uri 거부, 미지정 시 디폴트(첫 번째) 사용. **open redirect 방지**.

### `OAuth2FailureHandler`

디폴트 redirect URI에 `?error=<ErrorCode>` 붙여 302.

## 10. AuthController

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthController(private val userRepository: UserRepository) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<UserResponse> {
        val user = userRepository.findById(principal.userId).orElseThrow()
        return ApiResponse.success(UserResponse.from(user))
    }
}
```

## 11. 에러 코드

`com.team2.server.common.exception.ErrorCode`에 추가:

| 코드 | HTTP | 설명 |
|---|---|---|
| `AUTH_UNAUTHORIZED` | 401 | 토큰 없음 |
| `AUTH_INVALID_TOKEN` | 401 | 시그니처/형식 오류 |
| `AUTH_EXPIRED_TOKEN` | 401 | 만료 |
| `AUTH_OAUTH_FAILURE` | 401 | OAuth2 인증 실패 |
| `AUTH_USER_NOT_FOUND` | 401 | 토큰 sub로 user 미존재 |

## 12. 테스트 전략

목표 커버리지: `auth` 패키지 라인 80%+ (jacoco).

### 단위 테스트

- `JwtTokenProvider`: issue→parse 라운드트립, 만료, 시그니처 위조, 변조
- `KakaoAttributes`: 정상/email 미동의/profile 누락/account 누락 파싱
- `OAuth2AttributesFactory`: `kakao` registrationId → KakaoAttributes 반환, 미지원 provider → `OAuth2AuthenticationException`
- `OAuth2SuccessHandler`: redirect URL 토큰 포함, 화이트리스트 거부, 디폴트 폴백

### 슬라이스 / 통합 테스트

- `JwtAuthenticationFilter` (MockMvc):
  - 유효 Bearer → 200 + 인증 성공
  - 만료 / 잘못된 시그니처 / userId 미존재 → 401 + 정확한 ErrorCode
  - 헤더 없음 + permitAll → 200, 헤더 없음 + 보호 경로 → 401
- `SecurityConfig` (`@SpringBootTest` + MockMvc):
  - `/actuator/health` → 200
  - `/api/auth/me` 인증 없이 → 401, `@WithMockUser`로 → 200
  - `/oauth2/authorization/kakao` → 302 to `kauth.kakao.com`
- `User` 엔티티 (`@DataJpaTest`):
  - `(provider, providerId)` unique 제약
  - `findByProviderAndProviderId` 정상 조회

### `CustomOAuth2UserService`

`super.loadUser()` 모킹이 어려우므로, 카카오 응답 처리 로직(파싱 + upsert)을 분리한 헬퍼 메서드를 단위 테스트 대상으로 한다. 통합 테스트는 향후 WireMock 도입 시 추가.

## 13. 마이그레이션 / 운영 체크리스트

- **dev** (`ddl-auto: update`): `users.provider_id` 컬럼 자동 추가. 기존 row 있으면 NOT NULL 추가가 실패할 수 있음 → 사전 truncate 또는 nullable 추가 후 백필 수동 처리.
- **prod** (`ddl-auto: validate`): 수동 DDL 필요. 별도 마이그레이션 PR로 처리 (이번 PR 범위 밖, README에 명시).
- 카카오 디벨로퍼 콘솔에서 dev/prod 앱 각각 Redirect URI 등록 확인.
- `config/secret` 서브모듈에 시크릿 추가 + 푸시.

## 14. 비범위 (이번 PR 제외)

- Refresh token, 토큰 블랙리스트
- 로그아웃 API (stateless JWT는 클라이언트 토큰 폐기로 충분)
- 추가 provider (GOOGLE/APPLE/NAVER) **구현** — 구조(8장 전략 패턴)는 확장 가능하게 설계됨, 실제 구현체는 후속 PR
- prod DDL 마이그레이션 스크립트
- Rate limiting

## 15. 보안 고려

- **Open redirect**: SuccessHandler에서 redirect URI 화이트리스트 강제.
- **시크릿 노출**: 모든 시크릿은 `config/secret` 서브모듈(별도 권한 관리). 코드/공개 yml에 포함 금지.
- **JWT 시크릿**: 환경별 분리, 256bit+ (base64 인코딩).
- **CSRF**: 비활성. stateless JWT + 쿠키 미사용이라 안전. 향후 쿠키 기반 도입 시 재검토.
- **HTTPS**: 운영은 HTTPS 강제 (인프라 레벨).
- **카카오 동의**: `account_email`은 선택 동의 → 미동의 시 디폴트 이메일로 안전 폴백.
