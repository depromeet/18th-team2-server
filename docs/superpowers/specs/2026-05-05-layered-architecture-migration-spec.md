# Layered Architecture Migration — Implementation Spec

- 작성일: 2026-05-05
- 마지막 갱신: 2026-05-12 (rev. 2 — PR 1/PR 2 머지 반영 + chat 실측 기반 PR 7 재정의)
- 기반 설계: [2026-04-29-layered-architecture-design.md](./2026-04-29-layered-architecture-design.md)
- 대상: `com.team2.server` 전체
- 목표: 4-레이어 구조(`api / application{usecase,service,dto} / domain / infrastructure`)로 재배치 + ArchUnit 테스트 전부 활성화

---

## 0. 진척 현황 (2026-05-12 기준)

| 단계 | 상태 | 비고 |
|---|---|---|
| PR 1 — 스펙 머지 | ✅ 머지됨 | `5aff4f8 docs: 레이어드 아키텍처 마이그레이션 스펙 (점진 PR 전략)` |
| PR 2 — common 정리 | ✅ 머지됨 | `66df184 refactor: PR 2 — common 정리 (web/persistence/image 재배치)`. `common/{web, web/swagger, persistence, image/{entity,persistence}}` 완료, `ImageQueryService → ImageUrlReader` 리네임 + `@Transactional` 제거, `CommonPackageRule.X2` 활성 |
| PR 3 — party 4-레이어 | ⬜ 미착수 | 다음 차례 |
| PR 4 — user 4-레이어 | ⬜ 미착수 | — |
| PR 5 — auth 4-레이어 | ⬜ 미착수 | — |
| PR 6 — rollingpaper 4-레이어 | ⬜ 미착수 | — |
| PR 7 — chat 4-레이어 + cross-feature 정렬 | ⬜ 미착수 | 본 rev 에서 작업 항목 구체화 |
| PR 8 — ArchUnit 일괄 활성 | ⬜ 미착수 | — |

### 이번 rev (rev. 2) 의 변경 사항

1. **PR 1/PR 2 를 완료 상태로 표기.** 매핑 표에서 해당 항목에 ✅ 표시.
2. **chat 모듈 실측 기반 PR 7 재정의.** 스펙 작성 후 develop 에서 chat 풀-피처가 머지되면서 다음 ArchUnit 위반/조정 포인트가 확정됨:
   - `chat.usecase.EnterAndSubscribeChatUseCase → chat.usecase.EnterRealtimePartyUseCase` 호출 (D3 — 같은 feature UseCase 의존 위반)
   - chat UseCase 3종이 `party.repository.*`, `party.entity.*` 를 직접 참조 (cross-feature 직접 의존)
   - chat 에 Service 레이어 부재 → UseCase 가 Repository write 직접 호출 (D1 위반 예정)
   - PR 7 본문에 위 3건의 해소 방안을 구체적으로 추가.
3. **ArchUnit 활성 상태 표 수정.** A4/A5, H1, X1/X2 는 코드상 이미 활성(`@ArchIgnore` 없음). PR 2 머지로 X2 도 통과 중.
4. **`CustomOAuth2UserService` 처리 메모 보강.** B3 예외 조항 + C2 처리 충돌 가능성 사전 기록.

---

## 1. 의사결정 요약

| 항목 | 결정 |
|---|---|
| **PR 전략** | **점진 분할 PR** — feature 단위 8개 PR 로 쪼개 1-2일 단위로 머지. 단일 빅뱅 PR 은 두 번 실패(stale 브랜치 2개). 점진 PR 은 PR 1/PR 2 까지 무사고로 작동 중. |
| 작업 중 ArchUnit 처리 | 각 PR 에서 그 feature 가 만족하는 룰만 단계적으로 풀고, PR 8 에서 모든 `@ArchIgnore` 제거 |
| `image` | **common 잔류** — `common/image/` sub-namespace 로 그룹화 (PR 2 완료). polymorphic attachment 자원이라 별도 feature 승격 부적합. 정식 feature 승격 신호(업로드 API, 처리 파이프라인 등) 나타나면 별도 PR |
| `chat` / `rollingpaper` | develop 진척으로 둘 다 풀 피처 운영 중 — 각자 자기 feature 4-레이어 PR 에서 마이그레이션 |
| `@Transactional` | **엄격 적용** — Service / Initializer 에서 모두 제거, UseCase 에만. 조회 전용은 `readOnly = true`. 프레임워크 SPI 어댑터(`CustomOAuth2UserService`)는 `TransactionTemplate` 패턴 |
| DTO 분리 | api/dto 와 application/dto 분리. `*Request` / `*Response` 는 api, `*Command` / `*Result` 는 application. 단일 primitive 반환은 래핑 없이 (`Long`, `String`) |
| Service↔다른 feature | 금지. cross-feature 협업은 UseCase 가 다른 feature 의 UseCase / Service 를 조합 |
| 같은 feature UseCase↔UseCase | **금지 (D3).** orchestration 이 필요한 경우 controller 단 조합 또는 Service 추출 — chat PR 7 에서 첫 실 적용 |

---

## 2. 현재 → 목표 매핑

### 2-1. common ✅ (PR 2 머지 완료)

```
common/config/JpaConfig.kt              → common/config/ (유지)                        ✅
common/config/SwaggerConfig.kt          → common/config/ (유지)                        ✅
common/entity/BaseEntity.kt             → common/persistence/BaseEntity.kt              ✅
common/entity/Image.kt                  → common/image/entity/Image.kt                  ✅
common/entity/ImageTargetType.kt        → common/image/entity/ImageTargetType.kt        ✅
common/repository/ImageRepository.kt    → common/image/persistence/ImageRepository.kt   ✅
common/service/ImageQueryService.kt     → common/image/persistence/ImageUrlReader.kt    ✅
  - 리네임 + @Transactional 제거 (Spring Data repo 호출 자체가 암묵 transactional)
  - *Service suffix 제거로 PackageStructureTest.B3 위반 회피
common/exception/*                      → common/exception/ (유지)                     ✅
common/exception/DataIntegrityViolationExceptionExtensions.kt → 유지                    ✅
common/filter/MdcLoggingFilter.kt       → common/web/MdcLoggingFilter.kt                ✅
common/response/{ApiResponse, ErrorResponse} → common/web/                              ✅
common/exception/GlobalExceptionHandler.kt → common/web/GlobalExceptionHandler.kt       ✅
common/swagger/*                        → common/web/swagger/                          ✅
```

### 2-2. auth

```
auth/config/{JwtProperties, OAuth2Properties, SecurityConfig}
                                        → auth/infrastructure/security/
auth/jwt/{JwtAuthenticationEntryPoint, JwtAuthenticationFilter, JwtTokenProvider}
                                        → auth/infrastructure/security/
auth/oauth2/CustomOAuth2UserService     → auth/infrastructure/oauth2/
  - @Transactional 제거 → TransactionTemplate 으로 전환 (C2 룰 통과)
  - B3 룰: 클래스명 *Service 지만 DefaultOAuth2UserService 상속이라 PackageStructureTest 에서 areNotAssignableTo 예외 처리 이미 됨
auth/oauth2/{OAuth2SuccessHandler, OAuth2FailureHandler}
                                        → auth/api/oauth2/ (외부 콜백 진입점 — api 의미)
auth/oauth2/{OAuth2RedirectUriCaptureFilter, OAuth2RedirectUriCookies}
                                        → auth/infrastructure/oauth2/
auth/oauth2/attributes/*                → auth/domain/oauth2/
  - OAuth2 provider 응답을 도메인 입장에서 표현하는 VO. Spring Security `OAuth2User` 의존은 허용
auth/principal/UserPrincipal.kt         → auth/domain/UserPrincipal.kt
  - Spring Security `UserDetails` 결합 인정한 pragmatic 배치
auth/controller/AuthController.kt       → auth/api/AuthController.kt
auth/controller/DevTokenController.kt   → auth/api/DevTokenController.kt
auth/controller/UserResponse.kt         → auth/api/dto/UserResponse.kt
(신규) GetCurrentUserUseCase             → auth/application/usecase/  (AuthController.me 경유)
(신규) IssueDevTokenUseCase              → auth/application/usecase/  (DevTokenController 경유)
```

### 2-3. user

```
user/entity/User.kt                     → user/domain/entity/User.kt (AuthProvider enum 같이 따라감)
user/repository/UserRepository.kt       → user/infrastructure/persistence/UserRepository.kt
(신규) UserService                       → user/application/service/  (find/upsert — auth/chat UseCase 가 사용)
```

> user 의 별도 controller / endpoint 는 없음. UserService 는 cross-feature 호출 진입점 역할.

### 2-4. party

```
party/controller/* (10개)               → party/api/  (CharacterApi/Controller, MePartyApi/Controller, PartyApi/Controller, PartyInviteApi/Controller, PartyInviteLookupApi/Controller)
party/dto/* (Request/Response 계열)     → party/api/dto/
  - ActivateInviteLinkResponse, CharacterResponse, CreatePaperOnlyPartyRequest, CreatePartyResponse, CreateRealtimePartyRequest, PartyInviteLookupResponse, PartyInviteParticipationResponse, UpcomingPartyResponse
party/dto/CharacterResult.kt            → party/application/dto/CharacterResult.kt
party/dto/CharacterImageUrlResolver.kt  → party/infrastructure/CharacterImageResolver.kt
  - 클래스명 정렬: CharacterImageUrlResolver → CharacterImageResolver
party/entity/* (7개)                    → party/domain/entity/
  - Character, PaperOnlyParty, Participant, Party, PartyInvite, RealtimeParticipantProfile, RealtimeParty
party/repository/* (5개)                → party/infrastructure/persistence/
party/service/PartyService.kt           → party/application/service/PartyService.kt
party/service/PartyInviteService.kt     → party/application/service/PartyInviteService.kt
party/service/ParticipantService.kt     → party/application/service/ParticipantService.kt
party/usecase/* (4개)                   → party/application/usecase/
  - GetCharactersUseCase, GetUpcomingPartiesUseCase, JoinPartyInviteUseCase, LookupPartyInviteUseCase
(신규) CreatePartyUseCase                → party/application/usecase/  (PartyController.createParty 경유)
(신규) ActivateInviteLinkUseCase         → party/application/usecase/  (PartyInviteController 경유)
(신규) CreatePaperOnlyPartyCommand, CreateRealtimePartyCommand → party/application/dto/  (Request → Command 매핑)
(신규) PartyInviteLookupResult, RealtimeScheduleResult → party/application/dto/  (LookupPartyInviteUseCase 출력 — api/dto 의존 제거)
```

### 2-5. rollingpaper

```
rollingpaper/controller/* (6개)         → rollingpaper/api/
  - RollingPaperApi/Controller, RollingPaperOwnerApi/Controller, RollingPaperWrapperApi/Controller
rollingpaper/dto/{Request/Response}     → rollingpaper/api/dto/
  - CreateRollingPaperRequest, CreateRollingPaperResponse, OwnerRollingPaperDetailResponse, RollingPaperListResponse, RollingPaperWrapperResponse
rollingpaper/dto/RollingPaperWrapperResult.kt → rollingpaper/application/dto/
rollingpaper/entity/{RollingPaper, RollingPaperWrapper} → rollingpaper/domain/entity/
rollingpaper/repository/* (2개)         → rollingpaper/infrastructure/persistence/
rollingpaper/usecase/* (4개)            → rollingpaper/application/usecase/
  - CreateRollingPaperUseCase, GetRollingPaperDetailUseCase, GetRollingPaperListUseCase, GetRollingPaperWrappersUseCase
(신규) RollingPaperWrapperService         → rollingpaper/application/service/  (UseCase 가 직접 Repository 쓰기 호출 안 하도록 — D1 통과 위해)
```

> develop 의 `DefaultRollingPaperWrapperInitializer` 가 현재 main 소스 트리에 없음을 확인 (`*Initializer*` 매치 0건). 추후 재도입되면 `rollingpaper/infrastructure/bootstrap/` 에 배치 + `TransactionTemplate` 패턴 적용.

### 2-6. chat

```
chat/controller/{ChatApi, ChatController} → chat/api/
chat/dto/{Request/Response}             → chat/api/dto/
  - ChatMessageResponse, EnterRealtimePartyRequest, EnterRealtimePartyResponse, SendChatMessageRequest
chat/entity/ChatMessage.kt              → chat/domain/entity/ChatMessage.kt
chat/repository/ChatMessageRepository.kt → chat/infrastructure/persistence/
chat/service/SseEmitterRegistry.kt      → chat/infrastructure/sse/
chat/service/ChatMessageBroadcastEvent.kt → chat/infrastructure/sse/
chat/usecase/* (3개)                    → chat/application/usecase/
  - EnterAndSubscribeChatUseCase, EnterRealtimePartyUseCase, SendChatMessageUseCase
(신규) ChatMessageService                → chat/application/service/
  - 메시지 저장/조회/브로드캐스트 발행. UseCase 가 직접 ChatMessageRepository.save 를 부르지 않도록 분리 (D1 통과 위해)
```

> chat ↔ party cross-feature 의존 정렬은 별도 섹션(§3-5)에서 다룸.

---

## 3. 트랜잭션 / 도메인 행동 / cross-feature 재정렬

### 3-1. `@Transactional` 이전
- 모든 Service 클래스 / 메서드에서 `@Transactional` 제거
- UseCase 메서드(`invoke` / `execute` / `enter` / `send` 등)에 부착
  - 쓰기 흐름: `@Transactional`
  - 조회 흐름: `@Transactional(readOnly = true)`
- **Spring SPI 어댑터(`CustomOAuth2UserService`)는 `@Transactional` 미부착** — 필요시 `TransactionTemplate` 사용
- **Bootstrap 이니셜라이저(`infrastructure/bootstrap/`)는 `TransactionTemplate` 사용**

### 3-2. Controller → UseCase 직결
- 모든 Controller 는 UseCase 만 의존. Service / Repository 직접 호출 금지
- 현 develop 위반 사례: `AuthController` → `UserRepository`, `DevTokenController` → `UserRepository` + `JwtTokenProvider`, `PartyController` → `PartyService`, `PartyInviteController` → `PartyInviteService`. 각 feature PR 에서 UseCase 경유로 전환

### 3-3. DTO 레이어 컨벤션 (api ↔ application 분리)

**`api/dto/`**
- `*Request` — HTTP 입력 바디. validation/Swagger 어노테이션 가능
- `*Response` — HTTP 출력 바디. Jackson/Swagger 어노테이션 가능. `*Result` 로부터 Controller 가 매핑

**`application/dto/`** (각 feature 신규)
- `*Result` — UseCase/Service 출력. domain primitive/VO 조합. 직렬화/문서화 어노테이션 금지
- `*Command` — UseCase/Service 다중 필드 입력. Controller 가 `Request → Command` 매핑 후 호출

**프래그매틱 예외**
- 단일 primitive 반환(`Long`, `String`, `Boolean`)은 `*Result` 래핑하지 않고 그대로 반환
- 단일 primitive 입력(`partyId: Long`, `userId: Long`)은 `*Command` 만들지 않고 직접 파라미터

**금지**
- Service / UseCase 가 `..api..` 패키지 타입에 의존

### 3-4. 같은 feature UseCase↔UseCase 의존 금지 (D3)

원칙: orchestration 은 controller 합성 또는 Service 추출로 해결. 이번 마이그레이션에서 첫 실 적용 대상은 chat (§3-5).

### 3-5. chat ↔ party cross-feature + chat 내부 D3 위반 정렬 (PR 7 핵심)

**현 코드 실측 (`src/main/kotlin/com/team2/server/chat/usecase/*.kt`):**

| 위반 | 호출자 → 대상 | 룰 |
|---|---|---|
| 같은 feature UseCase 의존 | `EnterAndSubscribeChatUseCase` → `EnterRealtimePartyUseCase` | D3 |
| Repository 쓰기 직접 호출 | `EnterRealtimePartyUseCase` → `participantRepository.save`, `realtimeParticipantProfileRepository.save` | D1 |
| Repository 쓰기 직접 호출 | `SendChatMessageUseCase` → `chatMessageRepository.save` | D1 |
| cross-feature entity/repository 직접 참조 | chat UseCase 3종 → `party.entity.*`, `party.repository.*`, `user.repository.UserRepository` | E1/E2/E3 — 정렬 필요 |

**해소 방안:**

#### (a) `EnterAndSubscribeChatUseCase` 분해
- `EnterAndSubscribeChatUseCase` 의 역할은 (1) 라이브 입장 트랜잭션 + (2) SSE 구독 시작 두 가지. SSE 구독은 트랜잭션 밖이라 별도 UseCase 로 분리 가능.
- 변경:
  - `EnterRealtimePartyUseCase` — 트랜잭션 + 결과 반환. (기존)
  - `SubscribeChatStreamUseCase` (신규) — `partyId` 받아 history 로딩 + SseEmitter 생성/구독.
  - `ChatController` 가 둘을 순차 호출.
- 결과: chat 내 UseCase 간 의존 제거 (D3 통과).

#### (b) `ChatMessageService` 신설로 D1 해소
- `chat/application/service/ChatMessageService`:
  - `appendMessage(party, profile, content): ChatMessage` — `chatMessageRepository.save` 전담
  - `loadHistory(partyId): List<ChatMessage>` — fetch join 조회 위임
- `SendChatMessageUseCase`, `SubscribeChatStreamUseCase` 가 ChatMessageService 만 호출.
- `EnterRealtimePartyUseCase` 의 `participantRepository.save` / `profileRepository.save` 는 party feature 책임이므로 (c) 에서 party.Service 경유로 분리.

#### (c) party cross-feature 정렬
- `party/application/service/` 의 기존 Service 를 chat 도 호출하도록 정합화:
  - `ParticipantService.findOrCreate(party, userId): Participant` — `participantRepository.find/save` 캡슐화
  - `RealtimeParticipantProfileService` (신규 또는 ParticipantService 통합) — `profileRepository.find/save` 캡슐화
  - `PartyInviteService.resolveValidInvite(token): PartyInvite` — invite 조회 + 만료/실시간 검증
  - `PartyService.findRealtimePartyById(partyId): RealtimeParty` — `partyRepository.findPartyById` + `Hibernate.unproxy` + LIVE 상태 검증
- chat UseCase 는 위 party Service 들만 호출하고, `party.repository.*` / `party.entity.*` 직접 참조는 제거 (단, 도메인 entity 의 read-only 참조는 cross-feature E 룰이 허용하는 범위 내에서 유지 — `CrossFeatureRuleTest` 정의에 맞춰 PR 7 에서 최종 확인).
- `UserRepository` 직접 참조도 `user/application/service/UserService` 경유로 변경 (PR 4 에서 UserService 준비).

#### (d) PR 순서상 의존성
- PR 4 (user) 에서 `UserService` 신설 → PR 7 (chat) 에서 사용.
- PR 3 (party) 에서 `PartyService.findRealtimePartyById` 등 Service 보강 → PR 7 (chat) 이 의존.
- 따라서 PR 시퀀스는 **3 → 4 → 5 → 6 → 7 → 8** 순서 유지가 필수 (역순 불가).

---

## 4. ArchUnit 룰 활성화 매핑

각 PR 에서 해당 feature 의 정합성이 보장될 때 룰을 점진 활성. 마지막 PR 8 에서 모든 `@ArchIgnore` 제거.

| 룰 / 파일 | 상태 | 활성 시점 | 비고 |
|---|---|---|---|
| `LayerDependencyTest.domainShouldNotDependOnOuterLayers` (A4) | ✅ 활성 | PR 0 (이미) | `@ArchIgnore` 없음 — 코드 실측 확인 |
| `LayerDependencyTest.domainShouldNotDependOnSpringData` (A5) | ✅ 활성 | PR 0 (이미) | 동일 |
| `PackageCycleTest.H1` | ✅ 활성 | PR 0 (이미) | 동일 |
| `CommonPackageRuleTest.X1` | ✅ 활성 | PR 0 (이미) | 동일 |
| `CommonPackageRuleTest.X2` | ✅ 활성 | PR 2 머지 후 | 코드 실측 — `@ArchIgnore` 없음 |
| `ForbiddenCallRuleTest` 전부 (D1/D2/D3) | ⏳ 비활성 | PR 7 머지 후 | chat 정렬까지 끝나야 모든 feature D 룰 통과 가능. 잠정 PR 8 일괄 활성. (원안 "PR 3 활성" 은 chat 위반으로 불가) |
| `LayerDependencyTest.layeredArchitectureRules` (A1~A3) | ⏳ 비활성 | **PR 8** | 모든 feature 4-레이어 정렬 후 |
| `PackageStructureTest` 전부 (B1~B5) | ⏳ 비활성 | **PR 8** | `*Controller→api`, `*UseCase→application.usecase`, `*Service→application.service`, `*Repository→infrastructure.persistence` |
| `AnnotationRuleTest` 전부 (C1/C2) | ⏳ 비활성 | **PR 8** | `@RestController→api`, `@Transactional→application.usecase` |
| `CrossFeatureRuleTest` 전부 (E1/E2/E3) | ⏳ 비활성 | **PR 8** | chat 정렬 완료 전제 |

> rev. 1 의 "D 룰은 PR 3 활성" 항목은 chat 의 D1/D3 위반이 미해소 상태라 불가능. rev. 2 에서 PR 8 일괄 활성으로 변경.

---

## 5. PR 시퀀스 (점진 분할)

각 PR 은 1-2일 이내 머지 목표. 모든 PR 에서 `./gradlew build` 통과 필수. 각 PR 마다 `src/test/kotlin/**` 패키지 동기 이동.

### PR 1 — 스펙 머지 ✅ 머지됨
- 마이그레이션 결정/매핑 표 + ArchUnit phase 계획을 develop 에 기록
- 코드 변경 없음

### PR 2 — common 정리 ✅ 머지됨
- `common/web` 신설, `common/web/swagger/` 중첩
- `common/persistence/BaseEntity` 이동
- `common/image/{entity,persistence}` 신설, `Image`/`ImageTargetType`/`ImageRepository`/`ImageUrlReader` 이동 + 리네임
- `ImageUrlReader` 의 `@Transactional` 제거
- `ArchUnitConstants.FEATURES` 정정 (만약 `image` 가 들어 있으면 제거)
- 활성: `CommonPackageRule.X2`

### PR 3 — party 4-레이어 (가장 큰 일반 feature)
- 모든 controller/dto/entity/repository/service/usecase 를 4-레이어 위치로 이동
- DTO 분리: `*Request`/`*Response` → api/dto, `CharacterResult` → application/dto
- 신규: `CreatePartyUseCase`, `ActivateInviteLinkUseCase`, `CreatePaperOnly/RealtimePartyCommand`, `PartyInviteLookupResult`, `RealtimeScheduleResult`
- Service 보강 (chat PR 7 의존 항목 사전 준비):
  - `PartyService.findRealtimePartyById(partyId)`
  - `ParticipantService.findOrCreate(party, userId)` 또는 동등 API
  - `PartyInviteService.resolveValidInvite(token)`
- `@Transactional` Service → UseCase 이전
- Controller → UseCase only

### PR 4 — user 4-레이어
- `entity/User` → `domain/entity/`, `repository/UserRepository` → `infrastructure/persistence/`
- 신규: `UserService` (`application/service/`) — `findById` / `upsert` 인터페이스. auth/chat PR 에서 사용

### PR 5 — auth 4-레이어
- controller/jwt/config/oauth2/principal/oauth2/attributes 모두 4-레이어 위치로
- `SuccessHandler/FailureHandler` → `api/oauth2/`, `CustomOAuth2UserService` → `infrastructure/oauth2/`
- `CustomOAuth2UserService` `@Transactional` 제거 → `TransactionTemplate`
- 신규: `GetCurrentUserUseCase`, `IssueDevTokenUseCase` — `AuthController`/`DevTokenController` 경유

### PR 6 — rollingpaper 4-레이어
- 모든 controller/dto/entity/repository/usecase 이동
- DTO 분리
- 신규: `RollingPaperWrapperService` — UseCase 가 직접 Repository 쓰기 호출 안 하도록 분리
- chat 과의 cross-feature 의존이 있다면 정렬

### PR 7 — chat 4-레이어 + cross-feature 정렬
- 패키지 이동: 컨트롤러/엔티티/리포지토리/SSE 컴포넌트를 4-레이어 위치로
- 신규: `ChatMessageService` (application/service) — `chatMessageRepository.save`, history fetch join 캡슐화
- `EnterAndSubscribeChatUseCase` 분해 → `EnterRealtimePartyUseCase` + 신규 `SubscribeChatStreamUseCase`. controller 가 두 UseCase 를 순차 호출 (D3 통과)
- chat UseCase 들의 `party.repository.*` / `party.entity.*` / `user.repository.UserRepository` 직접 참조 제거 → party/user 의 application.service 경유로 정렬
- chat UseCase 들의 `@Transactional` 위치 정합화 (`@Transactional` 은 UseCase 에만)

### PR 8 — ArchUnit 일괄 활성
- 모든 `@ArchIgnore` 제거
- `LayerDependencyTest.layeredArchitectureRules`, `PackageStructureTest` 전부, `AnnotationRuleTest` 전부, `ForbiddenCallRuleTest` 전부, `CrossFeatureRuleTest` 전부 활성
- 빌드/테스트 통과 확인

---

## 6. 위험 및 완화

| 위험 | 완화 |
|---|---|
| PR 사이 develop 진척과 충돌 | 각 PR 시작 전 develop 최신화 + rebase. PR 1-2일 머지 목표로 충돌 윈도우 단축 |
| 특정 feature 동시 작업 중 충돌 | 해당 feature 작업 진행 상황 사전 확인. 진행 중이면 다음 PR 로 미룸 |
| `@Transactional` 누락으로 트랜잭션 깨짐 | 각 PR 의 기존 통합 테스트 그대로 통과 강제 |
| Service → Service 호출 잔존 | PR 8 의 `ForbiddenCallRuleTest.D2` 활성으로 강제 검출 |
| 패키지 이동 중 import 누락 | 각 PR 끝에 `./gradlew build` + ktlint 통과 강제. `git mv` 로 history 보존 |
| 테스트 패키지 미동기화 | main 패키지 이동 시 `src/test/kotlin/**` 동일 구조로 이동 (각 PR 내 필수 작업) |
| ArchUnit 룰이 의도보다 엄격해 false positive | 룰 자체 수정은 별도 PR. 다만 분명한 버그면 해당 phase 내에서 수정 + 사유 명시 |
| chat ↔ party cross-feature 결합 (E 룰) | §3-5 의 (a)~(c) 단계로 해소. PR 7 시작 전 party.Service 시그니처 합의 (PR 3 에서 사전 합의 도출) |
| `EnterAndSubscribeChatUseCase` 분해 시 SSE 트랜잭션 경계 변경 | 기존 통합 테스트 (`chat/controller`, `chat/usecase` 테스트) 통과 강제. SSE 구독은 트랜잭션 밖이라 분리해도 의미 변화 없음 |
| `CustomOAuth2UserService` 의 B3/C2 충돌 | B3 룰에 `areNotAssignableTo("DefaultOAuth2UserService")` 예외 이미 반영. C2 위반 회피는 `@Transactional` 제거 + TransactionTemplate 사용 (PR 5) |

---

## 7. 완료 정의 (DoD — 전체 PR 시퀀스 완료 시점)

- [ ] 모든 feature 가 `api / application{usecase,service,dto} / domain / infrastructure` 4-레이어로 정리
- [ ] 모든 Service 에서 `@Transactional` 제거, UseCase 에만 부착 (Bootstrap/SPI 어댑터는 `TransactionTemplate`)
- [ ] 모든 Controller 는 UseCase 만 호출 (Service/Repository 직접 호출 0건)
- [ ] 같은 feature 내 UseCase → UseCase 호출 0건 (D3)
- [ ] UseCase 의 Repository 쓰기 메서드 직접 호출 0건 (D1)
- [ ] chat UseCase 가 `party.repository.*` / `party.entity.*` / `user.repository.*` 직접 참조 0건
- [ ] `@ArchIgnore` 0건, ArchUnit 7개 테스트 파일(`LayerDependencyTest`, `PackageCycleTest`, `CommonPackageRuleTest`, `PackageStructureTest`, `AnnotationRuleTest`, `ForbiddenCallRuleTest`, `CrossFeatureRuleTest`) 전부 통과
- [ ] `./gradlew build` 통과 (단위/통합 테스트 포함)
- [ ] 기존 API 응답 스펙 변경 없음 (계약 보존)
- [ ] `common` 에 feature-specific 도메인 엔티티 0건 (cross-cutting attachment 인 `Image` 는 `common/image/` 잠정 유지)
- [ ] `src/test/kotlin/**` 가 `src/main/kotlin/**` 구조와 동기화
- [ ] 모든 단위/통합 테스트 통과 (테스트 개수 회귀 없음)

---

## 8. 범위 외

다음은 본 마이그레이션 시퀀스에서 다루지 **않는다**:

- 헥사고날(Port/Adapter) 도입
- DB 스키마 변경
- 패키지 외 모듈 분리(`:auth`, `:party` 등 멀티모듈화)
- 새 의존성 추가
- 기존 Policy 외 신규 도메인 정책 도입
- `image` 정식 feature 승격 — `common/image/` 잠정 유지. 승격 신호(업로드 API, 처리 파이프라인, 소유권/할당량 등) 시점의 별도 PR
- 엔드포인트 신설 / 비즈니스 행동 변경 — 패키지 이동 + DTO/UseCase 도입에 한정. 신규 endpoint 는 별도 PR
- `chat ↔ party` 도메인 모델 자체 재설계 — Service 경유 우회만 수행. 도메인 경계 재정의는 후속 작업

---

## 9. 참고 — 폐기된 시도

본 마이그레이션은 빅뱅 단일 PR 방식으로 두 번 시도되었음:

1. `refactor/layered-architecture-migration` — 본 스펙의 1차 작성자 작업. Phase 0-4a 까지 진행 후 develop 진척(chat 풀-피처, rollingpaper 확장)과 충돌해 stale. 본 스펙의 결정 기록은 이 브랜치에서 이관.
2. `refactor/4-layered-architecture` — 병렬 시도. 단일 커밋으로 일부 패키지 이동. 다른 설계 결정(image=feature, common/web/swagger 중첩, auth/domain 평탄) 채택. 역시 stale.

두 시도의 실패 원인은 동일: **빅뱅이 활발히 개발되는 코드베이스의 작업 속도와 안 맞음**. 본 점진 PR 시퀀스가 그 교훈을 반영한 결정. PR 1/PR 2 무사고 머지로 점진 전략의 적합성 1차 검증됨.
