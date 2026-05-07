# Party Invite Lookup and Member Join API Design

- 작성일: 2026-05-04
- 기준 브랜치: `feature/party-invite-lookup`
- 목적: 공유 링크 진입 시 `inviteToken`만으로 초대장/파티 요약을 조회하고, 로그인 회원을 명시적으로 참여자로 저장하는 API 추가
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 1. 사용자 흐름

1. 사용자가 공유 링크로 진입한다.
2. 프론트가 링크의 `inviteToken`으로 초대장/파티 요약을 조회한다.
3. 프론트가 `inviteToken`과 party summary를 `localStorage`에 저장한다.
4. 초대장 조회 자체는 `participant`를 생성하지 않는다.
5. 로그인 회원이면 프론트가 조회 성공 직후 회원 참여 API를 호출해 `participant`를 생성/복원한다.
6. `"롤페 작성하기"` 클릭 시 rolling paper 작성 플로우로 이동한다.
7. `"파티 입장하기"` 클릭 시 realtime profile 설정/입장 플로우로 이동한다.

핵심 경계:

- 초대장 조회는 **공개 조회**다.
- 초대장 조회는 **participant 생성/복원 side effect가 없어야 한다**.
- 회원 자동 참여는 조회 GET에 숨기지 않고 **인증 회원 전용 POST**로 분리한다.
- 비회원은 초대장 조회 직후 participant를 생성하지 않는다.
- 만료된 초대 토큰이나 종료된 파티에는 회원 참여자를 생성하지 않는다.

---

## 2. 현재 코드 기준

현재 `develop` 기준으로 확인한 구조:

- 초대 링크 발급 API: `POST /api/v1/parties/{partyId}/invite-link`
  - `PartyInviteController`
  - `PartyInviteService.activateInviteLink(...)`
- 현재 공개 허용:
  - `GET /api/v1/characters`
  - `GET /images/**`
- 현재 캐릭터 조회 응답은 `characterId`, `name`, `characterImageUrl`, `characterThumbnailImageUrl`을 내려준다.
  - `characterImageUrl`: 파티 입장 후 표시용 기본 이미지
  - `characterThumbnailImageUrl`: 캐릭터 선택 화면 등 작은 UI 표시용 썸네일 이미지
- 현재 `Party`는 abstract base entity이고 `RealtimeParty`, `PaperOnlyParty`가 joined inheritance로 분리되어 있다.
- 현재 `Party`에는 `partyOption: PartyOption`, `startedAt`, `createdAt`가 있고 `endedAt` 전용 컬럼은 없다.
- 현재 `party/usecase`에는 `GetCharactersUseCase`가 있으며, 조회 흐름을 컨트롤러에서 직접 조립하지 않고 유스케이스로 분리한 선례가 있다.

이번 기획과 달라지는 지점:

- 신규 조회 API 경로는 `GET /api/v1/party-invites/{inviteToken}`이다.
- 신규 회원 참여 API 경로는 `POST /api/v1/party-invites/{inviteToken}/participants/me`이다.
- 응답은 초대장 조회 전용 응답이다.
- `partyEnded`, `partyStartDate`, `partyEndDate`는 `endedAt` 전용 컬럼이 아니라 `Party.endedAt()` 기준으로 계산한다.
  - 현재 `Party.endedAt()`는 파티 종류와 관계없이 `startedAt + 7일`이다.
- `REALTIME` 파티는 조회 시점 상태값 대신 프론트가 버튼/카운트다운 상태를 계산할 기준 시각을 내려준다.

---

## 3. API 계약

### 3-1. Endpoint

```http
GET /api/v1/party-invites/{inviteToken}
```

### 3-2. 인증

- `permitAll`
- Authorization header가 없어도 조회 가능
- Authorization header가 유효하면 현재 회원의 `rollingPaperWritten` 계산에 사용
- Authorization header가 없거나 현재 조회자 식별이 불가능하면 `rollingPaperWritten = false`

주의:

- 현재 JWT 필터는 잘못된 Bearer 토큰이 들어오면 request attribute에 인증 오류를 기록한다.
- `permitAll` 경로라도 잘못된 Bearer 토큰은 기존 정책대로 401로 본다.
- 이번 API도 "Authorization header 없음은 익명 허용, invalid token은 401" 정책을 따른다.

### 3-3. Response

```json
{
  "partyId": 1,
  "celebrantNickname": "홍길동",
  "isHost": true,
  "partyOption": "REALTIME",
  "partyEnded": false,
  "rollingPaperWritten": false,
  "partyStartDate": "2026-05-04",
  "partyEndDate": "2026-05-11",
  "realtimeSchedule": {
    "liveStartAt": "2026-05-04T20:00:00",
    "enterableFrom": "2026-05-04T19:55:00",
    "liveEndAt": "2026-05-04T20:10:00",
    "liveDurationMinutes": 10
  }
}
```

`PAPER_ONLY` 응답:

```json
{
  "partyId": 1,
  "celebrantNickname": "홍길동",
  "isHost": false,
  "partyOption": "PAPER_ONLY",
  "partyEnded": false,
  "rollingPaperWritten": false,
  "partyStartDate": "2026-05-04",
  "partyEndDate": "2026-05-11",
  "realtimeSchedule": null
}
```

필드 매핑:

| 응답 필드 | source | 계산 |
|---|---|---|
| `partyId` | `Party.id` | 이후 주최자용 API, 화면 이동 등에 사용할 파티 식별자 |
| `celebrantNickname` | `Party.celebrantNickname` | 컬럼명과 동일 의미 유지 |
| `isHost` | `Party.ownerId` | 인증 회원이고 `party.ownerId == userId`이면 true, 비회원이면 false |
| `partyOption` | 현재 코드의 `Party.partyOption` | 그대로 |
| `partyEnded` | `Party.endedAt()` | `now >= Party.endedAt()` (`Party.endedAt()` = `startedAt + 7일`) |
| `rollingPaperWritten` | `Participant.hasWrittenPaper` | 식별 가능한 회원 participant가 있으면 해당 값, 없으면 false |
| `partyStartDate` | `Party.startedAt` | `startedAt.toLocalDate()` |
| `partyEndDate` | `Party.endedAt()` | `Party.endedAt().toLocalDate()` (`startedAt + 7일`) |
| `realtimeSchedule` | 실시간 파티 일정 기준 시각 | `REALTIME`이면 내려주고, `PAPER_ONLY`이면 null |
| `realtimeSchedule.liveStartAt` | `Party.startedAt` | 실시간 파티 시작 시각 |
| `realtimeSchedule.enterableFrom` | `Party.startedAt - 5분` | 프론트 입장 버튼 활성화 기준 시작 시각 |
| `realtimeSchedule.liveEndAt` | `Party.startedAt + 10분` | 프론트 종료 상태 기준 시각 |
| `realtimeSchedule.liveDurationMinutes` | 정책 상수 | 실시간 파티 진행 시간 |

### 3-4. 회원 참여 Endpoint

```http
POST /api/v1/party-invites/{inviteToken}/participants/me
```

인증:

- 로그인 회원 전용
- Authorization header가 없으면 401
- 잘못된 Bearer token은 기존 정책대로 401
- 요청 body는 없다.

응답:

```json
{
  "participantId": 1
}
```

처리 정책:

- `inviteToken`으로 `PartyInvite`를 조회한다.
- 토큰이 없으면 `PARTY_NOT_FOUND`.
- `PartyInvite.expiresAt`이 지났으면 `INVITE_LINK_EXPIRED`.
- `party.createdAt + 7일`이 지났으면 `PARTY_ENDED`.
- 로그인 회원의 기존 participant가 있으면 그대로 반환한다.
- 기존 participant가 없으면 `Participant(party, user)`를 생성한다.
- 기존 participant를 반환할 때는 `hasWrittenPaper`, `isCelebrant` 등 기존 participant 필드를 변경하지 않는다.
- 같은 회원이 동시에 호출해 `(party_id, user_id)` unique constraint가 발생하면 기존 participant를 다시 조회해 반환한다.
- 단, `uk_participant_party_user` 위반일 때만 기존 participant 재조회 복구를 수행하고, 다른 DB 제약 위반은 그대로 전파한다.
- 신규 생성과 기존 조회 모두 200으로 응답한다. 이 API는 "참여 상태 보장" 성격의 idempotent POST로 본다.

---

## 4. Realtime Schedule 설계

응답 DTO 전용 schedule:

```kotlin
data class RealtimeSchedule(
    val liveStartAt: LocalDateTime,
    val enterableFrom: LocalDateTime,
    val liveEndAt: LocalDateTime,
    val liveDurationMinutes: Long,
)
```

계산 규칙:

- `party.partyOption == PAPER_ONLY`이면 `realtimeSchedule = null`
- `REALTIME`일 때:
  - `liveStartAt = startedAt`
  - `enterableFrom = startedAt.minusMinutes(5)`
  - `liveEndAt = startedAt.plusMinutes(10)`
  - `liveDurationMinutes = 10`

설계 판단:

- 현재 `develop`에는 `RealtimeParty` 하위 타입이 있고 `RealtimeParty.status()`는 도메인 상태를 유지한다.
- 초대장 조회에서는 조회 시점의 `RealtimeStatus`를 내려주지 않는다.
- 프론트는 `enterableFrom`, `liveEndAt`과 현재 시각을 비교해 버튼 활성화/카운트다운/종료 표시를 계산한다.
- 백엔드는 이후 실시간 파티 입장 API에서 같은 기준으로 최종 입장 가능 여부를 검증한다.
- 5분 전 입장 가능 정책은 `RealtimeParty.status()`에 넣지 않고 초대장 조회 응답 schedule 계산에만 둔다.
- `RealtimeParty.startedAt`은 non-null이므로 REALTIME 정상 데이터에서는 null 방어가 필요 없다.

---

## 5. 토큰 유효성 정책

결정:

- 기존 `PartyInviteService.activateInviteLink(...)`는 유효한 초대 토큰 재사용을 위해 `PartyInvite.expiresAt`을 사용한다.
- 신규 기획은 "실시간 파티 종료 여부와 관계없이 `realtimeSchedule`을 내려준다"고 되어 있고, `partyEnded`는 `Party.endedAt()` 기준이다.
- 초대장 조회 API는 `PartyInvite.expiresAt`이 지났어도 조회 가능하게 한다.

구현 기준:

- 조회 API는 `PartyInviteRepository.findByToken(...)`으로 token 존재 여부만 확인한다.
- `PartyInvite.expiresAt`은 "초대 링크 발급/재사용 및 참여 가능성"에 쓰고, 초대장 요약 조회 자체를 막지는 않는다.
- 만료된 토큰도 파티 요약을 보여줘야 프론트가 `realtimeSchedule.liveEndAt` 또는 `partyEnded = true` 같은 기준으로 상태 화면을 안정적으로 만들 수 있다.
- 없는 token은 기존 관례대로 `PARTY_NOT_FOUND`를 반환한다.
- 회원 참여 API는 실제 participant 저장이므로 `PartyInvite.expiresAt`과 `party.isEnded(now)`를 모두 검증한다.

기존 발급/재사용 정책과 분리하기 위해 조회 전용 `findByToken` 흐름을 둔다.

---

## 6. 패키지/의존 설계

현재 코드 패키지 구조:

```text
party/
├── controller
├── dto
├── entity
├── repository
├── service
└── usecase
```

결정:

- 이번 변경은 **기존 코드 패키지 구조에 맞춰서 최소 추가**한다.
- 새 `application.usecase` 패키지는 만들지 않는다.
- 새 조회 흐름은 기존 `party/usecase/GetCharactersUseCase` 패턴을 따른다.
- `PartyInviteService`는 초대 토큰 발급/재사용 행위를 계속 담당하고, 초대장 조회는 별도 `LookupPartyInviteUseCase`가 담당한다.
- 회원 참여 흐름은 별도 `JoinPartyInviteUseCase`가 담당한다.
- participant 저장/복원과 DB unique constraint 복구는 `ParticipantService`가 담당한다.

추가 파일 위치:

```text
party/
├── controller/
│   ├── PartyInviteLookupApi.kt
│   └── PartyInviteLookupController.kt
├── dto/
│   ├── PartyInviteLookupResponse.kt
│   └── PartyInviteParticipationResponse.kt
├── service/
│   └── ParticipantService.kt
└── usecase/
    ├── LookupPartyInviteUseCase.kt
    └── JoinPartyInviteUseCase.kt
```

의존 방향:

```text
controller -> usecase -> repository/entity/dto
controller -> usecase -> service -> repository/entity
```

지킬 것:

- Controller는 Repository를 직접 보지 않는다.
- Controller는 기존 `CharacterController`처럼 UseCase만 호출한다.
- 조회 UseCase는 필요한 Repository만 직접 주입한다.
- 쓰기 UseCase는 participant 저장/복원 행위를 `ParticipantService`에 위임한다.
- UseCase는 HTTP 타입, `HttpServletRequest`, `AuthenticationPrincipal`을 모른다.
- DTO 변환은 조회 전용 UseCase 내부에서만 수행한다. 단, Controller에 변환 로직을 두지 않는다.
- 신규 조회 UseCase는 participant 생성/수정/save를 하지 않는다.
- 신규 조회 UseCase는 `PartyInviteService.activateInviteLink(...)`를 호출하지 않는다.

트랜잭션:

- 현재 `GetCharactersUseCase` 패턴에 맞춰 `LookupPartyInviteUseCase`의 조회 메서드에 `@Transactional(readOnly = true)`를 둔다.
- `JoinPartyInviteUseCase`에는 participant 저장이 있으므로 `@Transactional`을 둔다.
- public 메서드명은 컨트롤러에서 의미가 드러나도록 `lookup(...)`을 사용한다.
- 조회 유스케이스이므로 Repository write method는 호출하지 않는다.

---

## 7. 구현 상세안

### 7-1. Controller

새 컨트롤러:

```text
src/main/kotlin/com/team2/server/party/controller/PartyInviteLookupController.kt
```

역할:

- `GET /api/v1/party-invites/{inviteToken}` 매핑
- `POST /api/v1/party-invites/{inviteToken}/participants/me` 매핑
- `@AuthenticationPrincipal principal: UserPrincipal?`를 optional로 받음
- 유스케이스에 `inviteToken`, `principal?.userId` 전달
- `ApiResponse.success(...)`로 감쌈

기존 `PartyInviteController`는 유지:

- `POST /api/v1/parties/{partyId}/invite-link`만 담당
- 신규 조회 API를 여기에 섞지 않음

### 7-2. Swagger API interface

새 interface:

```text
src/main/kotlin/com/team2/server/party/controller/PartyInviteLookupApi.kt
```

문서화할 응답:

- 404: `PARTY_NOT_FOUND`
- 500: 공통 서버 오류
- 회원 참여 API는 400: `INVITE_LINK_EXPIRED`, `PARTY_ENDED`, 401: 인증 실패, 404: `PARTY_NOT_FOUND`, 500을 문서화한다.

성공 응답은 `ApiResponse<PartyInviteLookupResponse>` 반환 타입으로 자동 매칭되게 두고, Swagger에 200 응답을 수동 작성하지 않는다.
Enum 필드는 DTO `@Schema` 설명에 값별 의미를 명시한다.

만료된 토큰도 조회 가능하므로 `INVITE_LINK_EXPIRED`는 이 API의 Swagger 응답으로 문서화하지 않는다.

### 7-3. UseCase

새 유스케이스:

```text
src/main/kotlin/com/team2/server/party/usecase/LookupPartyInviteUseCase.kt
```

의존성:

- `PartyInviteRepository`
- `ParticipantRepository`

흐름:

1. `partyInviteRepository.findByToken(inviteToken)`
2. 없으면 `BusinessException(ErrorCode.PARTY_NOT_FOUND)`
3. `party = invite.party`
4. `partyEndAt = party.endedAt()`
5. `isHost` 계산
6. `rollingPaperWritten` 계산
7. `realtimeSchedule` 계산
8. `PartyInviteLookupResponse` 반환

의존성 제한:

- `LookupPartyInviteUseCase`는 `PartyInviteRepository`, `ParticipantRepository`만 주입하는 것을 우선한다.
- `isHost`는 `Party.ownerId`와 `userId`만 비교하므로 별도 Repository가 필요 없다.
- `rollingPaperWritten`은 `userId`만 있으면 `ParticipantRepository.findByPartyIdAndUserId(...)`로 계산 가능하므로 `UserRepository`를 주입하지 않는다.
- 이렇게 하면 조회 흐름이 user aggregate를 직접 조회하지 않아도 되고, 의존성이 더 작아진다.
- 단, 유효한 JWT인데 DB에서 user가 삭제된 케이스는 JWT 필터 단계에서 이미 `AUTH_USER_NOT_FOUND`로 처리되는 현재 구조를 따른다.

`rollingPaperWritten` 계산:

- `userId == null`이면 false
- 회원이면 `ParticipantRepository.findByPartyIdAndUserId(party.id, userId)`를 추가해서 조회
- participant가 없으면 false
- participant가 있으면 `hasWrittenPaper`

Repository 추가:

```kotlin
fun findByPartyIdAndUserId(
    partyId: Long,
    userId: Long,
): Participant?
```

이 추가는 기존 `existsByPartyIdAndUserId`와 같은 축이라 자연스럽다.

회원 참여 UseCase:

```text
src/main/kotlin/com/team2/server/party/usecase/JoinPartyInviteUseCase.kt
```

의존성:

- `PartyInviteRepository`
- `UserRepository`
- `ParticipantService`

흐름:

1. `partyInviteRepository.findByToken(inviteToken)`
2. 없으면 `BusinessException(ErrorCode.PARTY_NOT_FOUND)`
3. `PartyInvite.expiresAt` 검증
4. `party.isEnded(now)` 검증
5. `userRepository.findByIdOrNull(userId)`로 회원 조회
6. `participantService.joinMember(party, user)`로 회원 participant 생성/복원
7. `PartyInviteParticipationResponse(participantId)` 반환

`ParticipantService`:

- `joinMember(party, user)`는 기존 participant가 있으면 그대로 반환한다.
- 없으면 `Participant(party = party, user = user)`를 `saveAndFlush`로 저장한다.
- `uk_participant_party_user` 위반은 기존 participant 재조회로 복구한다.
- `uk_participant_party_user`가 아닌 `DataIntegrityViolationException`은 복구하지 않고 그대로 던진다.
- unique constraint 위반 후 재조회해도 participant가 없으면 원 예외를 다시 던진다.
- 기존 participant를 반환하는 경우 `hasWrittenPaper`, `isCelebrant` 같은 상태 필드는 갱신하지 않는다.

### 7-4. DTO

새 DTO:

```text
src/main/kotlin/com/team2/server/party/dto/PartyInviteLookupResponse.kt
```

Kotlin 타입:

- `partyId: Long`
- `celebrantNickname: String?`
- `isHost: Boolean`
- `partyOption: PartyOption`
- `partyEnded: Boolean`
- `rollingPaperWritten: Boolean`
- `partyStartDate: LocalDate`
- `partyEndDate: LocalDate`
- `realtimeSchedule: RealtimeSchedule?`

`RealtimeSchedule` 타입:

- `liveStartAt: LocalDateTime`
- `enterableFrom: LocalDateTime`
- `liveEndAt: LocalDateTime`
- `liveDurationMinutes: Long`

현재 엔티티 필드명도 `partyOption`이므로 DTO 필드명도 그대로 `partyOption`으로 둔다.
`partyOption`은 Swagger 필드 설명에 enum 값별 의미를 함께 적는다.

### 7-5. SecurityConfig

추가:

```kotlin
auth.requestMatchers(HttpMethod.GET, "/api/v1/party-invites/*").permitAll()
```

회원 참여 API:

- `POST /api/v1/party-invites/*/participants/me`는 별도 `permitAll`을 추가하지 않는다.
- 기존 `anyRequest().authenticated()`에 의해 로그인 회원만 접근한다.

현재 `develop`에는 `GET /api/v1/parties/{inviteToken}`가 없다. Swagger 문서에도 이 API를 새로 유지하거나 복구하지 않는다.

---

## 8. 테스트 계획

### 8-1. Controller 통합 테스트

새 테스트:

```text
src/test/kotlin/com/team2/server/party/controller/PartyInviteLookupControllerTest.kt
```

검증:

- 인증 없이 `PAPER_ONLY` 초대장 조회 성공
- 인증 없이 조회해도 participant count가 증가하지 않음
- 인증 회원 participant가 있고 `hasWrittenPaper = true`이면 `rollingPaperWritten = true`
- participant가 없으면 `rollingPaperWritten = false`
- 인증 회원이 회원 참여 API를 호출하면 participant 생성
- 이미 참여한 회원이 회원 참여 API를 다시 호출하면 기존 participant 반환
- 인증 없이 회원 참여 API를 호출하면 401
- 만료된 초대 토큰으로 회원 참여 API를 호출하면 participant 미생성 및 `INVITE_LINK_EXPIRED`
- 종료된 파티로 회원 참여 API를 호출하면 participant 미생성 및 `PARTY_ENDED`
- `PAPER_ONLY`는 `realtimeSchedule`이 null
- `REALTIME`은 `realtimeSchedule.liveStartAt`, `enterableFrom`, `liveEndAt`, `liveDurationMinutes`가 내려감
- `Party.endedAt()` 이상이면 `partyEnded = true`
- 없는 token이면 404 `PARTY_NOT_FOUND`

### 8-2. UseCase 단위 테스트

유스케이스 로직이 커지면 별도 단위 테스트를 둔다.

검증:

- token 없음
- token 존재 but no user
- token 존재 and participant exists
- date/schedule 경계값

`partyEnded` 경계 테스트는 `LocalDateTime.now()` 직접 호출 때문에 흔들릴 수 있다.

구현 선택:

- 단순 구현: startedAt을 현재 기준으로 충분히 멀리 잡아 통합 테스트 안정화
- 더 엄밀한 구현: `Clock` 주입 또는 `TimeProvider` 도입

구현 기준:

- 이번 기능 범위에서는 새 시간 추상화는 만들지 않는다.
- `realtimeSchedule` 테스트는 같은 `startedAt` 기준에서 `enterableFrom`, `liveEndAt` 기대값을 계산한다.

### 8-3. Security 테스트

검증:

- `GET /api/v1/party-invites/{inviteToken}`는 토큰 없이 200
- `POST /api/v1/party-invites/{inviteToken}/participants/me`는 토큰 없이 401
- 없는 token도 인증 없이 404까지 도달
- invalid Bearer token은 기존 보안 정책대로 401

### 8-4. 전체 테스트

실행:

```bash
./gradlew test
```

---

## 9. 구현 순서

승인 후 진행 순서:

1. `PartyInviteLookupResponse`, `RealtimeSchedule` DTO 추가
2. `PartyInviteParticipationResponse` DTO 추가
3. `ParticipantRepository.findByPartyIdAndUserId(...)` 추가
4. `ParticipantService` 추가
5. `LookupPartyInviteUseCase` 추가
6. `JoinPartyInviteUseCase` 추가
7. `PartyInviteLookupApi`, `PartyInviteLookupController` 추가
8. `SecurityConfig`에 `GET /api/v1/party-invites/*` permitAll 추가
9. controller/usecase/security 테스트 추가
10. 필요한 Swagger 예시 정리
11. 검증 실행
   - `./gradlew test --tests com.team2.server.party.controller.PartyInviteLookupControllerTest`
   - 필요 시 `./gradlew test`

---

## 10. 확정 사항

1. `PartyInvite.expiresAt`이 지난 token도 초대장 조회는 허용한다.
2. 현재 `develop`에는 기존 `GET /api/v1/parties/{inviteToken}`가 없으므로 유지/삭제 대상이 아니다.
3. 이번 기능은 기존 패키지 구조에 맞춰 작게 추가한다.
4. invalid Bearer token이 들어온 공개 조회 요청은 기존 정책대로 401로 본다.
5. `RealtimeParty.startedAt`은 현재 모델에서 non-null이므로 null 비정상 데이터 케이스는 별도 처리하지 않는다.
6. 회원 참여 API는 만료된 초대 토큰 또는 종료된 파티에는 participant를 생성하지 않는다.
