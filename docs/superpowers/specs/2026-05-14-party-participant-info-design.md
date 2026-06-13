# Party Participant Info (Realtime Profile) API Design

- 작성일: 2026-05-14
- 기준 브랜치: `develop`
- 목적: 실시간 파티 입장 화면에서 참가자(회원)의 닉네임/캐릭터를 작성·수정하는 API 계약 확정
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 1. 결정 요약

회원의 실시간 파티 입장 프로필(닉네임 + 캐릭터)을 `party-invites/{inviteToken}/participants/me/realtime-profile` 경로의 GET/PUT 두 엔드포인트로 다룬다.

이유:

- 사용자는 초대링크 진입 → `participants/me` 회원 참여 → 입장 대기 화면 흐름으로 진입한다. `inviteToken` 경로를 그대로 유지하면 프론트가 들고 있는 토큰을 그대로 쓸 수 있고, 기존 `participants/me`, `rolling-papers` 패턴과 정합한다.
- 입장 화면은 진입할 때 "기존 닉네임/캐릭터"를 보여줘야 한다(특히 주최자/재진입 참가자). 별도 GET이 필요하다.
- 작성·수정은 idempotent upsert 한 번으로 처리할 수 있어 PUT 단일 메서드가 충분하다.

주최자(`Participant.isCelebrant = true`)는 파티 생성 시점에 `RealtimeParticipantProfile`이 이미 존재하고, 닉네임은 `Party.celebrantNickname`으로 고정된다. 입장 화면에서 닉네임 변경 시도는 명시적으로 400으로 거부한다(같은 값 재전송은 허용).

이유:

- Figma 사양: "사전 설정한 닉네임 수정 불가". 서버가 silent하게 무시하면 클라이언트 버그를 가린다.
- 동일 값 재전송 허용은 입장 버튼을 누를 때마다 같은 body를 보내는 클라이언트 흐름의 멱등성 유지를 위함.

캐릭터는 항상 변경 가능(주최자 포함).

비회원은 이번 범위에 포함하지 않는다.

이유:

- 기존 `POST /api/v1/party-invites/{inviteToken}/participants/me`가 회원 전용이고, 비회원 회원 흐름은 guest 식별자 정책이 아직 정해지지 않았다. 별도 설계로 분리한다.

---

## 2. 사용자 흐름

1. 사용자가 초대링크로 진입한다.
2. 프론트가 `GET /api/v1/party-invites/{inviteToken}`로 파티 요약을 조회한다.
3. 로그인 회원이면 프론트가 `POST /api/v1/party-invites/{inviteToken}/participants/me`로 회원 participant를 보장한다.
4. 프론트가 입장 화면 진입 시 `GET /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile`로 현재 프로필 상태를 조회한다.
5. 화면 렌더링:
   - 주최자(`isHost = true`)이면 닉네임 input disabled로 prefill, 캐릭터는 변경 가능
   - 회원 참가자 첫 진입이면 닉네임 빈값, 캐릭터는 기본값(첫 캐릭터) 미선택 상태에서 사용자가 선택
   - 회원 참가자 재진입이면 닉네임/캐릭터 prefill
6. 사용자가 닉네임/캐릭터를 입력하고 "파티 입장하러 가기"를 누른다.
7. 프론트가 `PUT /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile`로 upsert한다.
8. 응답 성공 시 프론트는 실제 파티 입장 화면으로 이동한다.

핵심 경계:

- 입장 프로필 GET/PUT은 실시간 파티(`PartyOption.REALTIME`)에 한정된다. `PAPER_ONLY`이면 400으로 실패한다.
- 회원 participant가 아직 없으면 GET은 fallback으로 `ParticipantService.joinMember`를 호출해 participant를 생성하고 빈 프로필 상태를 응답한다. (`rolling-papers` 작성 API의 fallback 패턴과 동일)
- PUT도 fallback으로 participant를 생성/복원한다.
- 주최자의 닉네임은 파티 생성 시점에 `RealtimeParticipantProfile.nickname = Party.celebrantNickname`으로 설정된다. PUT 요청에 다른 닉네임이 오면 400으로 거부한다.
- 만료된 초대 토큰 또는 종료된 파티에는 GET/PUT 모두 실패한다(생성된 participant도 만들지 않는다).

---

## 3. 입력 정책

### 3-1. 닉네임

- 필수
- blank이면 실패
- 최대 10자
- 한국어, 영어, 숫자, 특수문자를 모두 허용한다. 정규식 문자 종류 제한 없음.
- 파티 내 unique constraint 없음(v1). 실시간 파티 참가자 닉네임은 채팅 화면에서 캐릭터와 함께 표시되므로 중복 자체가 치명적이지 않다. 향후 요구사항이 생기면 별도 설계.

저장 정책:

- 요청 DTO는 `@NotBlank`, `@Size(max = 10)` 기준으로 검증한다. trim 전 길이가 10자를 초과하면 `@Size` 실패로 처리한다(rolling paper 작성 정책과 동일).
- 서버는 앞뒤 공백을 제거한 닉네임을 `realtime_participant_profile.nickname`에 저장한다. 내부 공백은 유지.

### 3-2. 캐릭터

- 필수
- 존재하지 않는 `characterId`이면 `CHARACTER_NOT_FOUND` 실패.
- 모든 참가자(주최자 포함) 변경 가능.

---

## 4. API 계약

### 4-1. 입장 프로필 조회

```http
GET /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile
```

인증:

- 로그인 회원 전용 (Bearer required)
- invalid Bearer token은 기존 정책대로 401

응답:

```json
{
  "status": 200,
  "data": {
    "participantId": 1,
    "isHost": false,
    "nickname": "안녕용가리",
    "nicknameEditable": true,
    "character": {
      "characterId": 1,
      "name": "기본",
      "characterImageUrl": "/images/characters/basic.png",
      "characterThumbnailImageUrl": "/images/characters/basic_thumb.png"
    }
  }
}
```

필드:

| 필드 | 설명 |
|---|---|
| `participantId` | 회원 participant ID |
| `isHost` | `participant.isCelebrant` 값. 주최자=true |
| `nickname` | 현재 저장된 닉네임. 아직 프로필이 없으면 `null` |
| `nicknameEditable` | `!isHost`. 주최자는 false |
| `character` | 현재 선택된 캐릭터. 프로필이 없거나 캐릭터 미선택이면 `null` |

처리 흐름:

1. `inviteToken`으로 `PartyInvite` 조회.
2. 토큰 없으면 `PARTY_NOT_FOUND`.
3. 만료된 토큰이면 `INVITE_LINK_EXPIRED`.
4. 파티 종료됐으면 `PARTY_ENDED`.
5. 파티가 `REALTIME`이 아니면 `PARTY_NOT_REALTIME`.
6. `ParticipantService.joinMember(party, user)`로 회원 participant 조회·생성·복원.
7. `RealtimeParticipantProfileRepository.findByParticipant(participant)`로 프로필 조회.
8. 프로필이 없으면 `nickname = null`, `character = null` 응답.
9. 프로필이 있으면 저장된 값을 응답. 캐릭터 이미지 URL은 `ImageRepository`로 한 번에 조회(N+1 없음).

트랜잭션:

- UseCase 메서드 전체에 `@Transactional`. participant fallback 생성이 일어날 수 있으므로 readOnly가 아님.

### 4-2. 입장 프로필 작성·수정

```http
PUT /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile
```

인증:

- 로그인 회원 전용 (Bearer required)
- invalid Bearer token은 기존 정책대로 401

요청:

```json
{
  "nickname": "안녕용가리",
  "characterId": 1
}
```

응답:

```http
HTTP/1.1 200 OK
```

```json
{
  "status": 200,
  "data": {
    "participantId": 1,
    "isHost": false,
    "nickname": "안녕용가리",
    "nicknameEditable": true,
    "character": {
      "characterId": 1,
      "name": "기본",
      "characterImageUrl": "/images/characters/basic.png",
      "characterThumbnailImageUrl": "/images/characters/basic_thumb.png"
    }
  }
}
```

작성·수정 후 최신 프로필 상태를 GET과 동일한 형태로 내려준다. 프론트가 PUT 직후 GET을 추가 호출하지 않아도 화면을 일관되게 유지할 수 있다.

처리 흐름:

컨트롤러 진입 시점:

1. 요청 DTO validation을 수행한다.
2. `nickname`은 `@NotBlank`, `@Size(max = 10)`로 검증한다.
3. `characterId`는 `@NotNull`로 검증한다.

UseCase 진입 이후:

1. `inviteToken`으로 `PartyInvite` 조회.
2. 토큰 없으면 `PARTY_NOT_FOUND`.
3. 만료된 토큰이면 `INVITE_LINK_EXPIRED`.
4. 파티 종료됐으면 `PARTY_ENDED`.
5. 파티가 `REALTIME`이 아니면 `PARTY_NOT_REALTIME`.
6. `characterId`로 `Character` 조회. 없으면 `CHARACTER_NOT_FOUND`.
7. `ParticipantService.joinMember(party, user)`로 회원 participant 조회·생성·복원.
8. 닉네임 trim.
9. `RealtimeParticipantProfileRepository.findByParticipant(participant)`로 기존 프로필 조회.
10. 기존 프로필이 있고 `participant.isCelebrant = true`이면:
    - trim된 nickname이 기존 `profile.nickname`과 다르면 `PARTY_HOST_NICKNAME_NOT_EDITABLE` 실패.
    - 같으면 캐릭터만 갱신.
11. 기존 프로필이 있고 `isCelebrant = false`이면 nickname/character 모두 갱신.
12. 기존 프로필이 없으면 새 `RealtimeParticipantProfile` 생성(`isCelebrant`와 무관 — 주최자는 파티 생성 시점에 이미 생성되므로 일반적으로 이 분기는 회원 참가자만 도달).
13. 캐릭터 이미지 URL 응답을 구성한다.

트랜잭션 경계:

- UseCase의 upsert 메서드 전체에 `@Transactional`을 둔다.
- `PartyInvite` 조회, 만료/종료 검증, `Character` 조회, participant 생성·복원, 프로필 upsert는 모두 같은 트랜잭션에서 처리한다.
- 중간 단계에서 실패하면 participant 변경과 프로필 변경은 함께 롤백되어야 한다.

동시성:

- `RealtimeParticipantProfile`은 `participant_id` unique constraint를 가진다.
- 같은 회원이 동시에 두 번 PUT을 보내 두 프로필이 동시에 INSERT 되면 unique constraint로 한 건은 실패한다.
- 회원 participant 생성·복원 중 `uk_participant_party_user` 위반은 `ParticipantService.joinMember`가 재조회로 복구한다.
- 프로필 INSERT 충돌은 같은 트랜잭션 내에서 처리하기 어렵다(DB 제약 위반 시점에는 영속성 컨텍스트가 더러워진 상태). 충돌은 `BusinessException`이 아닌 일반적인 동시 요청 실패로 본다. 클라이언트는 사용자에게 단순 재시도 안내가 가능하다. 별도 ErrorCode를 추가하지 않고 `INTERNAL_SERVER_ERROR`로 응답한다.
- 실제 운영에서는 같은 회원이 같은 화면에서 동시에 두 번 PUT을 보낼 가능성이 낮으므로 v1에서 이 경계는 수용 가능하다고 본다.

---

## 5. 엔티티 기준 변경점

엔티티 자체 변경은 없다. 기존 `RealtimeParticipantProfile`(`participant_id` unique, `nickname` varchar(20), `character_id` nullable)을 그대로 사용한다.

`realtime_participant_profile.nickname` 컬럼은 현재 varchar(20)이다. API에서는 `@Size(max = 10)`로 검증한다. DB 컬럼을 줄이지 않는 이유:

- 기존 데이터(주최자 프로필)는 `Party.celebrant_nickname`(varchar(255))을 그대로 복사해 저장되며 10자 초과 데이터가 존재할 수 있다.
- 입력 측 길이 제한을 강하게 두고, 저장 컬럼 크기는 여유를 두는 편이 안전하다.
- 이번 범위에서 schema migration을 추가하지 않는다.

`Participant`, `Party`, `Character`도 변경 없음.

---

## 6. 추가할 코드 구조

기존 `party` 패키지에 최소 추가한다.

```text
party/
├── controller/
│   ├── ParticipantRealtimeProfileApi.kt        # 신규
│   └── ParticipantRealtimeProfileController.kt # 신규
├── dto/
│   ├── ParticipantRealtimeProfileResponse.kt   # 신규
│   └── UpsertParticipantRealtimeProfileRequest.kt # 신규
├── service/
│   └── RealtimeParticipantProfileService.kt    # 신규
└── usecase/
    ├── GetMyRealtimeProfileUseCase.kt          # 신규
    └── UpsertMyRealtimeProfileUseCase.kt       # 신규
```

의존 방향(레이어드 아키텍처 규칙 준수):

```text
Controller -> UseCase -> Service / Repository / Domain / DTO
```

지킬 것:

- Controller는 Repository를 직접 보지 않는다. UseCase만 호출한다.
- 두 UseCase는 각각 1 public 메서드(`invoke`)만 노출한다.
- 두 UseCase 모두 `@Transactional`을 가진다(GET도 participant fallback 때문에 readOnly가 아님).
- Service는 자기 aggregate만 다룬다.
  - `ParticipantService.joinMember`로 participant 조회·생성·복원
  - 신규 `RealtimeParticipantProfileService`는 `RealtimeParticipantProfile` aggregate의 조회·생성·갱신을 책임진다.
- Service에 `@Transactional` 선언 금지. UseCase에서 트랜잭션 경계를 잡는다.
- UseCase가 Service 간 조합을 담당한다.
- 이미지 URL 해석은 기존 `GetCharactersUseCase`처럼 `ImageRepository`와 `ImageTargetType.CHARACTER`를 사용한다.
- 응답 DTO 변환은 UseCase 책임이다(Service는 도메인 객체 반환).
- 응답 DTO는 GET/PUT 공통으로 `ParticipantRealtimeProfileResponse`를 사용한다.

`RealtimeParticipantProfileService` 책임:

- `findByParticipant(participant): RealtimeParticipantProfile?`
- `upsert(participant: Participant, nickname: String, character: Character, isHostNicknameLocked: Boolean): RealtimeParticipantProfile`
  - 내부 분기:
    - 기존 프로필 없음 → 새로 생성·저장
    - 기존 프로필 있음 + `isHostNicknameLocked = true` + nickname 변경 시도 → `PARTY_HOST_NICKNAME_NOT_EDITABLE` throw
    - 기존 프로필 있음 + locked = true + nickname 동일 → 캐릭터만 갱신
    - 기존 프로필 있음 + locked = false → nickname/character 모두 갱신
- `@Transactional` 선언 없음(UseCase에서 잡음).

UseCase가 Service에 `isHostNicknameLocked` 플래그를 전달해 정책을 분기시키는 이유:

- 닉네임 잠금 정책은 "주최자 = 닉네임 변경 불가"라는 도메인 규칙이고, 정책 자체는 UseCase에서 결정한다(`participant.isCelebrant` 기반).
- Service는 실제 저장 로직을 다루고, 닉네임이 잠긴 상태인지를 외부에서 받아 행위를 분기한다.
- Service가 직접 `participant.isCelebrant`를 읽어 분기해도 무방하지만, `participant`를 보고 행위가 바뀌는 분기를 Service 안에서 키우면 추후 다른 정책(예: 운영자 강제 변경)이 들어왔을 때 분기 폭이 커진다. 플래그로 외부에서 결정한다.

---

## 7. ErrorCode 추가안

```kotlin
PARTY_NOT_REALTIME(HttpStatus.BAD_REQUEST, "실시간 파티가 아닙니다")
PARTY_HOST_NICKNAME_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "주최자 닉네임은 변경할 수 없습니다")
```

기존 ErrorCode 재사용:

| 상황 | ErrorCode | HTTP status |
|---|---|---|
| 토큰 없음 | `PARTY_NOT_FOUND` | 404 |
| 초대 토큰 만료 | `INVITE_LINK_EXPIRED` | 400 |
| 파티 종료 | `PARTY_ENDED` | 400 |
| request validation 실패 | `INVALID_INPUT` (`@Valid` 실패 → 글로벌 핸들러) | 400 |
| 캐릭터 없음 | `CHARACTER_NOT_FOUND` | 404 |
| invalid Bearer token | `AUTH_INVALID_TOKEN` | 401 |
| 회원 아님 | `AUTH_UNAUTHORIZED` | 401 |
| 사용자 없음 | `AUTH_USER_NOT_FOUND` | 401 |

---

## 8. SecurityConfig

추가 경로(인증 필요, 기본 `anyRequest().authenticated()`에 포함되므로 별도 `permitAll` 추가하지 않는다):

```http
GET /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile
PUT /api/v1/party-invites/{inviteToken}/participants/me/realtime-profile
```

`/api/v1/party-invites/{inviteToken}/participants/me`와 동일한 보안 정책을 따른다.

---

## 9. Swagger 문서화

GET 입장 프로필 조회:

- 200: 조회 성공(프로필 없음 포함)
- 400: 초대 링크 만료, 파티 종료, 비-실시간 파티
- 401: 인증 실패, 사용자 없음
- 404: 파티 없음
- 500: 공통 서버 오류

PUT 입장 프로필 작성·수정:

- 200: upsert 성공
- 400: validation 실패, 초대 링크 만료, 파티 종료, 비-실시간 파티, 주최자 닉네임 변경 시도
- 401: 인증 실패, 사용자 없음
- 404: 파티 없음, 캐릭터 없음
- 500: 공통 서버 오류

---

## 10. 테스트 계획

GET 입장 프로필 조회:

- 회원 참가자 첫 진입 → participant 생성, `nickname = null`, `character = null`, `nicknameEditable = true`
- 회원 참가자 재진입(프로필 있음) → 저장된 값 반환
- 주최자 진입 → `nickname = celebrantNickname`, `nicknameEditable = false`, 캐릭터 prefill
- 만료된 초대 토큰 → `INVITE_LINK_EXPIRED`, participant 새로 만들지 않음
- 종료된 파티 → `PARTY_ENDED`
- 비-실시간 파티 → `PARTY_NOT_REALTIME`
- 존재하지 않는 inviteToken → `PARTY_NOT_FOUND`
- invalid Bearer token → 401

PUT 입장 프로필 작성·수정:

- 회원 참가자 첫 작성 → 200, 프로필 생성, 저장된 값 응답
- 회원 참가자 수정(nickname/character 모두 변경) → 200, 프로필 갱신
- 닉네임 누락/blank → 400
- 닉네임 10자 초과(공백 포함, 공백 제외 모두) → 400
- trim 후 동일한 닉네임 → 정상 저장(trim된 값 저장)
- `characterId` 누락 → 400
- 없는 `characterId` → 404 `CHARACTER_NOT_FOUND`
- 주최자가 같은 닉네임 + 캐릭터만 변경 → 200, 캐릭터만 갱신
- 주최자가 다른 닉네임 변경 시도 → 400 `PARTY_HOST_NICKNAME_NOT_EDITABLE`, 변경 없음
- 만료된 초대 토큰 → 400 `INVITE_LINK_EXPIRED`, participant·프로필 변경 없음
- 종료된 파티 → 400 `PARTY_ENDED`
- 비-실시간 파티 → 400 `PARTY_NOT_REALTIME`
- 존재하지 않는 inviteToken → 404 `PARTY_NOT_FOUND`
- invalid Bearer token → 401
- 같은 회원이 동시에 두 번 PUT → 한 건은 성공, 다른 한 건은 unique constraint 위반(`INTERNAL_SERVER_ERROR` 또는 두 번째 요청이 update로 동작 — Hibernate flush 타이밍에 따라 다름. 핵심은 데이터 무결성이 깨지지 않는 것.)

레이어드 아키텍처 ArchUnit:

- 기존 ArchUnit 규칙(controller→usecase, usecase→service, service→자기 aggregate)에 새 클래스들이 자동 검증된다.
- 추가 ArchUnit 규칙은 도입하지 않는다.

---

## 11. 후속 작업(이번 범위 외)

- 비회원 입장 프로필: guest 식별자 정책이 정해진 뒤 별도 설계
- 주최자가 파티 생성 후 자신의 닉네임 자체를 바꾸는 기능: 별도 "파티 수정" API 범위
- 사전 롤링페이퍼 작성자 닉네임을 참가자 닉네임 기본값으로 가져오는 동작(Figma 노트 참조): 사전 롤링페이퍼 기능 자체가 정해진 뒤 별도 설계. 이번 API는 빈 값 또는 저장된 값 prefill만 책임진다.
