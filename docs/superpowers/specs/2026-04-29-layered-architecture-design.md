# Layered Architecture Design — UseCase + Service 분리

- 작성일: 2026-04-29
- 대상: `com.team2.server` (Kotlin + Spring Boot)
- 목적: 기능별로 일관된 4-레이어 구조와 명확한 책임 분리 규칙 확립

---

## 1. 결정 사항

전 패키지를 다음 4-레이어 구조로 통일한다.

```
feature/
├── api/                  HTTP 진입점, Request/Response DTO
├── application/
│   ├── usecase/          유스케이스 (흐름 제어, 트랜잭션 경계)
│   └── service/          Aggregate 단위 행위 (재사용 단위)
├── domain/
│   ├── entity/           행동 있는 JPA 엔티티
│   ├── policy/           여러 엔티티 걸친 도메인 규칙
│   └── vo/               enum, value object
└── infrastructure/
    └── persistence/      JpaRepository, 외부 어댑터
```

핵심 원칙: **UseCase = 흐름, Service = 행위**.

- UseCase: 여러 Aggregate를 어떻게 엮을지
- Service: 한 Aggregate를 어떻게 다룰지

---

## 2. 의존성 규칙

### 2-1. 의존 방향
```
api ──▶ application.usecase ──▶ application.service ──▶ infrastructure
                  │                       │
                  └───────▶ domain ◀──────┘
```

### 2-2. 의존성 매트릭스

| From ↓ \ To → | UseCase | Service | Repository | Domain | 다른 feature UseCase | 다른 feature Service |
|---|---|---|---|---|---|---|
| Controller (api) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| UseCase | ❌ | ✅ | ⚠️ 조회만 | ✅ | ✅ | ❌ |
| Service | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Domain | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Infrastructure | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |

핵심:
- Controller는 UseCase만 안다
- UseCase는 Service를 조합한다
- Service는 자기 Aggregate의 Repository와 Domain만 안다
- Cross-feature 호출은 **UseCase ↔ UseCase**만 허용

---

## 3. UseCase 규칙

### 3-1. 형태
- 1 UseCase = 1 클래스 = 1 public 메서드 (`invoke` 또는 `execute`)
- `@Transactional`은 **UseCase에만** 선언 (Service에는 절대 금지)
- 클래스 길이 **60줄 이내**, 생성자 의존성 **5개 이내**

### 3-2. UseCase의 5단계
```
① 입력 → 도메인 식별자 변환 (필요 시)
② Service/조회로 도메인 객체 획득
③ 도메인 규칙 호출 (Entity 메서드, Policy)
④ Service로 행위 위임 (저장/변경)
⑤ 응답 DTO 변환
```

### 3-3. UseCase가 하면 안 되는 것
| 금지 | 대신 |
|---|---|
| `Repository.save / saveAndFlush / delete` | Service에 위임 |
| `try/catch DataIntegrityViolationException` | Service 내부에서 처리 |
| `entity.field = value` 도메인 변경 | Service의 도메인 행위 메서드 |
| 다른 feature의 Repository 직접 호출 | 그 feature의 UseCase 주입 |
| 같은 feature 다른 UseCase 호출 | 금지 — 공통 로직은 Service로 |

### 3-4. UseCase 비대화 한도
- 클래스 길이 60줄
- 생성자 의존성 5개
- private helper 1개 이하 (그 이상은 추출 신호)

---

## 4. Service 규칙

### 4-1. 형태
- 1 Aggregate = 1 Service (`PartyService`, `ParticipantService`, `InviteService`)
- 모호한 이름 금지 (`PartyManagementService` ❌)
- 클래스 길이 **150줄 이내**, 생성자 의존성 **4개 이내**, public 메서드 **5개 이내**

### 4-2. Service 메서드는 도메인 동사
| ✅ 좋은 이름 | ❌ 나쁜 이름 |
|---|---|
| `participantService.join(...)` | `participantService.create(...)` |
| `inviteService.activate(...)` | `inviteService.save(...)` |
| `inviteService.findValid(...)` | `inviteService.get(...)` |
| `partyService.close(...)` | `partyService.update(...)` |

### 4-3. Service가 하는 일
1. Aggregate 생성/조회/변경/삭제 (Repository 직접 호출)
2. Aggregate 도메인 규칙 호출 (`party.assertJoinable()`)
3. 인프라 예외 → 도메인 예외 변환
4. **도메인 객체 반환** (Response DTO 변환은 UseCase 책임)

### 4-4. Service가 하면 안 되는 것
| 금지 | 이유 |
|---|---|
| `@Transactional` | 트랜잭션 단일 진실점은 UseCase |
| 다른 feature의 Repository/Service 호출 | feature 경계 침범 |
| 같은 feature 내 다른 Service 호출 | 의존 지옥 방지 — 조합은 UseCase가 |
| Response DTO 반환 | DTO는 application 경계의 일 |
| HTTP 정보 접근 (`HttpServletRequest`, principal) | Web 영역은 Controller |

### 4-5. Service ↔ Service 호출 금지
다른 Aggregate를 참조해야 하면 **인자로 받는다**.
```kotlin
// ✅ OK
fun ParticipantService.join(party: Party, user: User?, ...): Participant

// ❌ NO
class ParticipantService(private val partyService: PartyService, ...)
```

---

## 5. Domain 규칙

### 5-1. Entity는 행동을 가진다 (anemic 모델 금지)
```kotlin
@Entity
class Party(...) : BaseEntity() {
    fun assertJoinable(now: LocalDateTime) {
        if (endedAt?.let { !it.isAfter(now) } == true)
            throw BusinessException(ErrorCode.PARTY_ENDED)
    }
    fun requiresCharacter(): Boolean = isChattingAllow
}
```

### 5-2. 여러 엔티티 걸친 규칙은 Policy
```kotlin
@Component
class CharacterSelectionPolicy {
    fun validate(party: Party, characterId: Long?) {
        when {
            party.requiresCharacter() && characterId == null ->
                throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
            !party.requiresCharacter() && characterId != null ->
                throw BusinessException(ErrorCode.CHARACTER_NOT_ALLOWED)
        }
    }
}
```

### 5-3. Domain은 Spring 의존 최소화
- JPA 어노테이션은 허용 (현실적 타협)
- `@Service`, `@Component`는 Policy에만 (Stateless 도메인 서비스 한정)
- Repository 의존 금지

---

## 6. 패키지 트리 (목표)

```
com.team2.server
│
├── ServerApplication.kt
│
├── common/
│   ├── config/         (JpaConfig, SwaggerConfig)
│   ├── web/            (ApiResponse, ErrorResponse, GlobalExceptionHandler, MdcLoggingFilter)
│   ├── exception/      (BusinessException, ErrorCode)
│   └── persistence/    (BaseEntity)
│
├── auth/
│   ├── api/            (AuthController, DevTokenController, dto/)
│   ├── application/
│   │   ├── usecase/    (ProcessOAuth2LoginUseCase)
│   │   └── service/    (JwtIssueService)
│   ├── domain/         (UserPrincipal, OAuth2Attributes, KakaoAttributes, OAuth2AttributesFactory)
│   └── infrastructure/
│       ├── security/   (SecurityConfig, JwtFilter/EntryPoint/Provider/Properties)
│       └── oauth2/     (CustomOAuth2UserService, Success/FailureHandler)
│
├── user/
│   ├── api/
│   ├── application/
│   │   ├── usecase/    (UpsertOAuth2UserUseCase)
│   │   └── service/    (UserService)
│   ├── domain/         (User, AuthProvider)
│   └── infrastructure/persistence/  (UserRepository)
│
├── party/
│   ├── api/            (PartyController, PartyInviteController, dto/)
│   ├── application/
│   │   ├── usecase/    (CreatePartyUseCase, GetPartyInfoUseCase, JoinPartyUseCase, ActivateInviteLinkUseCase)
│   │   └── service/    (PartyService, ParticipantService, InviteService)
│   ├── domain/
│   │   ├── entity/     (Party, Participant, PartyInvite, Character)
│   │   ├── policy/     (PartyJoinPolicy, CharacterSelectionPolicy)
│   │   └── vo/         (PartyOption, PartyPurpose)
│   └── infrastructure/
│       ├── persistence/  (PartyRepository, ParticipantRepository, PartyInviteRepository, CharacterRepository)
│       ├── CharacterImageResolver.kt
│       └── SecureRandomInviteTokenGenerator.kt
│
├── image/
│   ├── domain/         (Image, ImageTargetType)
│   └── infrastructure/persistence/  (ImageRepository)
│
├── chat/               (4-레이어 골격만, 미구현)
│   ├── api/
│   ├── application/
│   ├── domain/         (ChatMessage)
│   └── infrastructure/persistence/
│
└── rollingpaper/       (4-레이어 골격만, 미구현)
    ├── api/
    ├── application/
    ├── domain/         (RollingPaper, RollingPaperWrapper)
    └── infrastructure/persistence/
```

---

## 7. 의사결정 플로우차트

새 메서드를 어디에 둘까?
```
HTTP 입출력?              ──▶ Controller (api)
여러 Aggregate 조합 흐름?  ──▶ UseCase
한 Aggregate의 행위?       ──▶ Service
Aggregate 자체의 불변식?   ──▶ Domain Entity
여러 Entity 걸친 규칙?     ──▶ Domain Policy
```

---

## 8. 책임 분배 — Join Party 예시

| 책임 | 위치 |
|---|---|
| `@Transactional` 시작/종료 | `JoinPartyUseCase` |
| 흐름 순서 제어 | `JoinPartyUseCase` |
| `@NotBlank`, `@Size` 입력 검증 | `api/dto/JoinPartyRequest` (`@Valid`) |
| `Party.endedAt > now` 검증 | `Party.assertJoinable()` (domain) |
| 캐릭터 선택 규칙 | `CharacterSelectionPolicy` (domain) |
| 초대 토큰 만료 검증 | `PartyInvite.assertNotExpired()` (domain) |
| 초대 조회 + 만료 검증 호출 | `InviteService.findValid()` |
| 중복 가입 검증 | `ParticipantService.join()` 내부 |
| 참여자 저장 + DB 제약 위반 처리 | `ParticipantService.join()` |
| 응답 DTO 변환 | `JoinPartyUseCase` (`ParticipantResponse.from(...)`) |
| HTTP 200 / ErrorCode → HTTP status | `PartyController` + `GlobalExceptionHandler` |

---

## 9. 코드 골격 — Join Party

```kotlin
// application/usecase/JoinPartyUseCase.kt
@Service
class JoinPartyUseCase(
    private val inviteService: InviteService,
    private val participantService: ParticipantService,
    private val userQueryService: UserQueryService,
    private val characterSelectionPolicy: CharacterSelectionPolicy,
    private val characterImageResolver: CharacterImageResolver,
) {
    @Transactional
    fun invoke(token: String, userId: Long?, nickname: String, characterId: Long?): ParticipantResponse {
        val invite = inviteService.findValid(token)
        val party = invite.party

        party.assertJoinable(LocalDateTime.now())
        characterSelectionPolicy.validate(party, characterId)

        val user = userId?.let(userQueryService::findOrThrow)
        val participant = participantService.join(party, user, nickname, characterId)

        val imageUrl = participant.character?.let(characterImageResolver::resolve)
        return ParticipantResponse.from(participant, imageUrl)
    }
}

// application/service/InviteService.kt
@Service
class InviteService(
    private val partyInviteRepository: PartyInviteRepository,
    private val tokenGenerator: InviteTokenGenerator,
) {
    fun findValid(token: String): PartyInvite {
        val invite = partyInviteRepository.findByToken(token)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        invite.assertNotExpired(LocalDateTime.now())
        return invite
    }

    fun activateFor(party: Party): PartyInvite {
        val now = LocalDateTime.now()
        return partyInviteRepository.findByPartyIdAndExpiresAtAfter(party.id, now)
            ?: partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    token = tokenGenerator.generate(),
                    expiresAt = party.endedAt ?: now.plusHours(EXPIRY_HOURS),
                )
            )
    }

    companion object { private const val EXPIRY_HOURS = 24L }
}

// application/service/ParticipantService.kt
@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val characterRepository: CharacterRepository,
) {
    fun join(party: Party, user: User?, nickname: String, characterId: Long?): Participant {
        if (user != null && participantRepository.existsByPartyAndUser(party, user))
            throw BusinessException(ErrorCode.ALREADY_JOINED)

        val character = characterId?.let {
            characterRepository.findById(it)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        }

        return try {
            participantRepository.saveAndFlush(
                Participant(party = party, character = character, user = user, nickname = nickname)
            )
        } catch (e: DataIntegrityViolationException) {
            if (e.isConstraintViolation(PARTICIPANT_UNIQUE_CONSTRAINT))
                throw BusinessException(ErrorCode.ALREADY_JOINED)
            throw e
        }
    }

    companion object { private const val PARTICIPANT_UNIQUE_CONSTRAINT = "uk_participant_party_user" }
}
```

---

## 10. 트랜잭션 & 예외 경계

```
Controller         예외 catch ❌  — 그대로 전파
UseCase            @Transactional 시작/끝 — 도메인 예외 그대로 전파
Service            인프라 예외 → 도메인 예외 변환
Domain             BusinessException 던짐
GlobalExceptionHandler  BusinessException → ErrorCode 기반 HTTP 응답
```

---

## 11. Cross-Feature 의존

다른 feature가 필요하면 **그 feature의 UseCase를 주입**한다.
```kotlin
// ✅ OK
class ProcessOAuth2LoginUseCase(
    private val upsertOAuth2UserUseCase: UpsertOAuth2UserUseCase,  // user feature
    private val jwtIssueService: JwtIssueService,
) { ... }

// ❌ NO
class ProcessOAuth2LoginUseCase(
    private val userRepository: UserRepository,  // 다른 feature infrastructure
)
```

UseCase가 다른 feature의 UseCase를 호출할 때 **별도 트랜잭션이 아닌, 같은 트랜잭션** 안에서 진행된다 (Spring 기본 PROPAGATION_REQUIRED).

---

## 12. 마이그레이션 우선순위

| 단계 | 범위 | 위험도 |
|---|---|---|
| 1 | `common` 정리 — `web/persistence` 분리, `image` feature 분가 | 낮음 |
| 2 | `party` 4-레이어 재배치 + UseCase/Service 분리 + 도메인 메서드 추출 | 중간 (핵심 도메인) |
| 3 | `user` application 레이어 신설 (`UserService`, `UpsertOAuth2UserUseCase`) | 중간 |
| 4 | `auth` 기술별 → 4-레이어 재배치 (security/oauth2 → infrastructure) | 중간 |
| 5 | `chat`, `rollingpaper` 골격 정비 | 낮음 |

각 단계는 별도 PR로 진행하며, PR마다 빌드+테스트 통과 후 머지한다.

---

## 13. 한 페이지 요약 (팀 가이드)

```
UseCase = 흐름 (1 클래스 = 1 메서드 invoke, @Transactional)
Service = 행위 (1 Aggregate = 1 Service, 도메인 동사 메서드)

UseCase ↛ UseCase (같은 feature)
Service ↛ Service (어디든)
Service ↛ 다른 feature (어디든)
UseCase → 다른 feature UseCase ✅

UseCase 한도: 60줄 / 의존성 5개
Service 한도: 150줄 / 의존성 4개 / public 메서드 5개

Repository 쓰기 = Service만
@Transactional = UseCase만
Response DTO 변환 = UseCase에서
도메인 규칙 = Entity / Policy
```
