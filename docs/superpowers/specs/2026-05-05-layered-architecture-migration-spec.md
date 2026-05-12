# Layered Architecture Migration — Implementation Spec

- 작성일: 2026-05-05
- 마지막 갱신: 2026-05-12 (develop 진척 반영 + PR 전략 점진으로 변경)
- 기반 설계: [2026-04-29-layered-architecture-design.md](./2026-04-29-layered-architecture-design.md)
- 대상: `com.team2.server` 전체
- 목표: 4-레이어 구조(`api / application{usecase,service,dto} / domain / infrastructure`)로 재배치 + ArchUnit 테스트 전부 활성화

---

## 1. 의사결정 요약

| 항목 | 결정 |
|---|---|
| **PR 전략** | **점진 분할 PR** — feature 단위 8개 PR로 쪼개 1-2일 단위로 머지. 단일 빅뱅 PR은 두 번 시도(stale 브랜치 2개) 실패. 팀 작업 속도와 충돌 윈도우 데이터로 결정 |
| 작업 중 ArchUnit 처리 | 각 PR 에서 그 feature 가 만족하는 룰만 단계적으로 풀고, 마지막 PR(룰 일괄 활성)에서 모든 `@ArchIgnore` 제거 |
| `image` | **common 잔류** — `common/image/` sub-namespace 로 그룹화. polymorphic attachment 자원이라 별도 feature 승격 부적합. 정식 feature 승격 신호(업로드 API, 처리 파이프라인 등) 나타나면 별도 PR |
| `chat` / `rollingpaper` | develop 진척으로 둘 다 풀 피처 운영 중 — 각자 자기 feature 4-레이어 PR 에서 마이그레이션 |
| `@Transactional` | **엄격 적용** — Service / Initializer 에서 모두 제거, UseCase 에만. 조회 전용은 `readOnly = true`. 프레임워크 SPI 어댑터(`CustomOAuth2UserService`)는 `TransactionTemplate` 패턴 |
| DTO 분리 | api/dto 와 application/dto 분리. `*Request` / `*Response` 는 api, `*Command` / `*Result` 는 application. 단일 primitive 반환은 래핑 없이 (`Long`, `String`) |
| Service↔다른 feature | 금지. cross-feature 협업은 UseCase 가 다른 feature UseCase / Service 를 조합 |

---

## 2. 현재 → 목표 매핑

### 2-1. common

```
common/config/JpaConfig.kt              → common/config/ (유지)
common/config/SwaggerConfig.kt          → common/config/ (유지)
common/entity/BaseEntity.kt             → common/persistence/BaseEntity.kt
common/entity/Image.kt                  → common/image/entity/Image.kt
common/entity/ImageTargetType.kt        → common/image/entity/ImageTargetType.kt
common/repository/ImageRepository.kt    → common/image/persistence/ImageRepository.kt
common/service/ImageQueryService.kt     → common/image/persistence/ImageUrlReader.kt
  - 리네임 + @Transactional 제거 (Spring Data repo 호출 자체가 암묵 transactional)
  - *Service suffix 제거로 PackageStructureTest.B3 위반 회피
common/exception/*                      → common/exception/ (유지)
common/exception/DataIntegrityViolationExceptionExtensions.kt → 유지
common/filter/MdcLoggingFilter.kt       → common/web/MdcLoggingFilter.kt
common/response/{ApiResponse, ErrorResponse} → common/web/
common/exception/GlobalExceptionHandler.kt → common/web/GlobalExceptionHandler.kt
common/swagger/*                        → common/web/swagger/  (응답 어노테이션 4종을 sub-folder 로)
```

### 2-2. auth

```
auth/config/{JwtProperties, OAuth2Properties, SecurityConfig}
                                        → auth/infrastructure/security/
auth/jwt/{JwtAuthenticationEntryPoint, JwtAuthenticationFilter, JwtTokenProvider}
                                        → auth/infrastructure/security/
auth/oauth2/CustomOAuth2UserService     → auth/infrastructure/oauth2/
  - @Transactional 제거 → TransactionTemplate 으로 전환 (C2 룰 통과)
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
(신규) UserService                       → user/application/service/  (find/upsert — auth UseCase 가 사용)
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
(신규) RollingPaperWrapperService         → rollingpaper/application/service/  (UseCase 가 직접 Repository 호출 안 하도록)
```

> develop 의 `DefaultRollingPaperWrapperInitializer` 는 마이그레이션 시점에 `service/` 에 없을 수 있음 (개발 진척 확인 필요). 있으면 `rollingpaper/infrastructure/bootstrap/` 으로 이동, `TransactionTemplate` 패턴 유지.

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
```

> chat UseCase 들이 `party.entity.RealtimeParticipantProfile` 등을 직접 참조하면 cross-feature 의존 발생 — 마이그레이션 시 `chat.application.usecase → party.application.service.ParticipantService` 또는 신규 cross-feature UseCase 호출로 정렬 필요. PR 단위에서 점검.

---

## 3. 트랜잭션 / 도메인 행동 재정렬

### 3-1. `@Transactional` 이전
- 모든 Service 클래스 / 메서드에서 `@Transactional` 제거
- UseCase 메서드(`invoke` / `execute`)에 부착
  - 쓰기 흐름: `@Transactional`
  - 조회 흐름: `@Transactional(readOnly = true)`
- **Spring SPI 어댑터(`CustomOAuth2UserService`)는 `@Transactional` 미부착** — 필요시 `TransactionTemplate` 사용
- **Bootstrap 이니셜라이저(`infrastructure/bootstrap/`)는 `TransactionTemplate` 사용** — 이미 `DefaultRollingPaperWrapperInitializer` 가 이 패턴

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

---

## 4. ArchUnit 룰 활성화 매핑

각 PR 에서 해당 feature 의 정합성이 보장될 때 룰을 점진 활성. 마지막 PR(룰 일괄 활성)에서 모든 `@ArchIgnore` 제거.

| 룰 / 파일 | 활성 시점 | 비고 |
|---|---|---|
| `LayerDependencyTest.domainShouldNotDependOnOuterLayers` (A4) | PR 1 머지 직후 | 즉시 활성 |
| `LayerDependencyTest.domainShouldNotDependOnSpringData` (A5) | PR 1 머지 직후 | 즉시 활성 |
| `PackageCycleTest.H1` | PR 1 머지 직후 | 즉시 활성 |
| `CommonPackageRuleTest.X1` | PR 1 머지 직후 | 즉시 활성 |
| `CommonPackageRuleTest.X2` | PR 2 (common 정리) | common.web/config 정리 후 |
| `ForbiddenCallRuleTest` 전부 (D1/D2/D3) | PR 3 (party) | 4-레이어 형태 갖춰지면 활성 가능 |
| `LayerDependencyTest.layeredArchitectureRules` (A1~A3) | **PR 8 (룰 일괄 활성)** | 모든 feature 가 4-레이어로 정렬된 후 |
| `PackageStructureTest` 전부 (B1~B5) | **PR 8** | `*Controller→api`, `*UseCase→application.usecase`, `*Service→application.service`, `*Repository→infrastructure.persistence` |
| `AnnotationRuleTest` 전부 (C1/C2) | **PR 8** | `@RestController→api`, `@Transactional→application.usecase` |
| `CrossFeatureRuleTest` 전부 (E1/E2/E3) | **PR 8** | cross-feature 의존 정합성 확인 후 |

---

## 5. PR 시퀀스 (점진 분할)

각 PR 은 1-2일 이내 머지 목표. 모든 PR 에서 `./gradlew build` 통과 필수. 각 PR 마다 `src/test/kotlin/**` 패키지 동기 이동.

### PR 1 — 스펙 머지 (본 문서)
- 마이그레이션 결정/매핑 표 + ArchUnit phase 계획을 develop 에 기록
- 코드 변경 없음

### PR 2 — common 정리
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
- `@Transactional` Service → UseCase 이전
- Controller → UseCase only
- 활성: `ForbiddenCallRuleTest` (D1/D2/D3)

### PR 4 — user 4-레이어
- `entity/User` → `domain/entity/`, `repository/UserRepository` → `infrastructure/persistence/`
- 신규: `UserService` (`application/service/`) — find/upsert 인터페이스. 다음 auth PR 에서 사용

### PR 5 — auth 4-레이어
- controller/jwt/config/oauth2/principal/oauth2/attributes 모두 4-레이어 위치로
- `SuccessHandler/FailureHandler` → `api/oauth2/`, `CustomOAuth2UserService` → `infrastructure/oauth2/`
- `CustomOAuth2UserService` `@Transactional` 제거 → `TransactionTemplate`
- 신규: `GetCurrentUserUseCase`, `IssueDevTokenUseCase` — `AuthController`/`DevTokenController` 경유

### PR 6 — rollingpaper 4-레이어
- 모든 controller/dto/entity/repository/usecase 이동
- DTO 분리
- 신규: `RollingPaperWrapperService` — UseCase 가 직접 Repository 호출 안 하도록 분리
- chat 과의 cross-feature 의존이 있다면 정렬

### PR 7 — chat 4-레이어
- 모든 controller/dto/entity/repository/usecase 이동
- `SseEmitterRegistry`, `ChatMessageBroadcastEvent` → `chat/infrastructure/sse/`
- party 와의 cross-feature 의존을 UseCase / Service 경유로 정렬

### PR 8 — ArchUnit 일괄 활성
- 모든 `@ArchIgnore` 제거
- `LayerDependencyTest.layeredArchitectureRules`, `PackageStructureTest` 전부, `AnnotationRuleTest` 전부, `CrossFeatureRuleTest` 전부 활성
- 빌드/테스트 통과 확인

---

## 6. 위험 및 완화

| 위험 | 완화 |
|---|---|
| PR 사이 develop 진척과 충돌 | 각 PR 시작 전 develop 최신화 + rebase. PR 1-2일 머지 목표로 충돌 윈도우 단축 |
| 특정 feature 동시 작업 중 충돌 | 해당 feature 작업 진행 상황 사전 확인. 진행 중이면 다음 PR 로 미룸 |
| `@Transactional` 누락으로 트랜잭션 깨짐 | 각 PR 의 기존 통합 테스트 그대로 통과 강제 |
| Service → Service 호출 잔존 | PR 3 에서 `ForbiddenCallRuleTest.D2` 활성으로 강제 검출 |
| 패키지 이동 중 import 누락 | 각 PR 끝에 `./gradlew build` + ktlint 통과 강제. `git mv` 로 history 보존 |
| 테스트 패키지 미동기화 | main 패키지 이동 시 `src/test/kotlin/**` 동일 구조로 이동 (각 PR 내 필수 작업) |
| ArchUnit 룰이 의도보다 엄격해 false positive | 룰 자체 수정은 별도 PR. 다만 분명한 버그면 해당 phase 내에서 수정 + 사유 명시 |
| chat ↔ party cross-feature 결합 | PR 7 작업 전에 cross-feature 의존 그래프 분석. 위반은 UseCase 경유로 우회 |

---

## 7. 완료 정의 (DoD — 전체 PR 시퀀스 완료 시점)

- [ ] 모든 feature 가 `api / application{usecase,service,dto} / domain / infrastructure` 4-레이어로 정리
- [ ] 모든 Service 에서 `@Transactional` 제거, UseCase 에만 부착 (Bootstrap/SPI 어댑터는 `TransactionTemplate`)
- [ ] 모든 Controller 는 UseCase 만 호출 (Service/Repository 직접 호출 0건)
- [ ] `@ArchIgnore` 0건, ArchUnit 7개 테스트 파일(`LayerDependency`, `PackageCycle`, `CommonPackageRule`, `PackageStructure`, `AnnotationRule`, `ForbiddenCall`, `CrossFeatureRule`) 전부 통과
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
- `chat ↔ party` cross-feature 결합도의 도메인 레벨 재설계 — 위반만 UseCase 경유로 우회

---

## 9. 참고 — 폐기된 시도

본 마이그레이션은 빅뱅 단일 PR 방식으로 두 번 시도되었음:

1. `refactor/layered-architecture-migration` — 본 스펙의 1차 작성자 작업. Phase 0-4a 까지 진행 후 develop 진척(chat 풀-피처, rollingpaper 확장)과 충돌해 stale. 본 스펙의 결정 기록은 이 브랜치에서 이관.
2. `refactor/4-layered-architecture` — 병렬 시도. 단일 커밋 으로 일부 패키지 이동. 다른 설계 결정(image=feature, common/web/swagger 중첩, auth/domain 평탄) 채택. 역시 stale.

두 시도의 실패 원인은 동일: **빅뱅이 활발히 개발되는 코드베이스의 작업 속도와 안 맞음**. 본 점진 PR 시퀀스가 그 교훈을 반영한 결정.
