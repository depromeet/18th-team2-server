# 파티 참여 및 캐릭터 선택 기능 설계

## 개요

초대 토큰(inviteToken)을 통해 회원/비회원이 파티 정보를 조회하고 파티에 참여하는 기능이다.

현재 서버 구현은 `PartyInvite.token` 기반으로 동작한다. 초대링크 활성화 API는 전체 URL을 만들지 않고 16자리 토큰만 반환하며, 프론트는 반환된 토큰을 사용해 파티 정보 조회와 파티 참여 API를 호출한다.

이번 문서의 범위는 다음과 같다.

- 현재 구현된 초대 토큰 기반 파티 정보 조회
- 현재 구현된 초대 토큰 기반 파티 참여
- 현재 구현된 참여 응답의 `characterImageUrl` 계약
- 현재 구현된 캐릭터 기본 데이터 및 이미지 URL 구조
- 이번 PR에서 추가할 캐릭터 조회 API 계약

## 현재 구현 요약

### 핵심 정책

- 파티 정보 조회와 파티 참여는 초대 토큰으로 접근한다.
- 인증은 선택사항이다.
- Authorization 헤더가 없으면 비회원 흐름으로 통과한다.
- Authorization 헤더가 유효하면 회원 흐름으로 처리한다.
- 회원은 같은 파티에 중복 참여할 수 없다.
- 비회원 중복 참여는 서버에서 차단하지 않는다.
- 만료된 초대 토큰은 파티 조회와 참여 모두 `INVITE_LINK_EXPIRED`로 거절한다.
- 종료된 파티는 조회는 가능하지만 참여는 불가능하다.
- 참여 응답은 `characterId`가 아니라 프론트에서 바로 사용할 수 있는 `characterImageUrl`을 반환한다.
- `isChattingAllow`는 응답 필드가 아니라 서버 내부 참여 검증 규칙으로 사용한다.

### 관련 구현 파일

| 역할 | 파일 |
|------|------|
| 파티 생성/조회 컨트롤러 | `src/main/kotlin/com/team2/server/party/controller/PartyController.kt` |
| 파티 API Swagger 문서 | `src/main/kotlin/com/team2/server/party/controller/PartyApi.kt` |
| 초대링크 활성화 컨트롤러 | `src/main/kotlin/com/team2/server/party/controller/PartyInviteController.kt` |
| 초대링크 활성화 서비스 | `src/main/kotlin/com/team2/server/party/service/PartyInviteService.kt` |
| 파티 정보 조회 서비스 | `src/main/kotlin/com/team2/server/party/service/PartyService.kt` |
| 파티 참여 서비스 | `src/main/kotlin/com/team2/server/party/service/PartyParticipationService.kt` |
| 파티 참여 요청 DTO | `src/main/kotlin/com/team2/server/party/dto/JoinPartyRequest.kt` |
| 참여자 응답 DTO | `src/main/kotlin/com/team2/server/party/dto/ParticipantResponse.kt` |
| 파티 정보 응답 DTO | `src/main/kotlin/com/team2/server/party/dto/PartyInfoResponse.kt` |
| 캐릭터 기본 데이터 초기화 | `src/main/kotlin/com/team2/server/party/service/DefaultCharacterInitializer.kt` |
| 보안 공개 경로 | `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` |

## 데이터 모델

### Party

`party` 테이블의 주요 필드는 다음과 같다.

| 필드 | 설명 |
|------|------|
| `id` | 파티 ID |
| `owner_id` | 파티 생성자 회원 ID |
| `name` | 파티 이름 |
| `celebrant_nickname` | 주인공 닉네임 |
| `started_at` | 파티 시작 일시 |
| `ended_at` | 파티 종료 일시 |
| `party_type` | 파티 옵션. 현재 enum은 `REALTIME`, `PAPER_ONLY` |
| `purpose` | 파티 목적. 현재 enum은 `BIRTHDAY`, `JOB_CHANGE`, `WEDDING` |
| `is_chatting_allow` | 캐릭터 선택 가능 여부를 판단하는 서버 내부 검증 값 |

현재 `PartyInfoResponse`에는 `isChattingAllow`를 내려주지 않는다.

### PartyInvite

`party_invite` 테이블은 초대 토큰을 저장한다.

| 필드 | 설명 |
|------|------|
| `party_id` | 초대 토큰이 연결된 파티 |
| `token` | 초대 토큰. 8바이트 랜덤값을 hex로 변환한 16자리 문자열 |
| `expires_at` | 초대 토큰 만료 일시 |

초대 토큰 만료 정책은 다음과 같다.

- 이미 유효한 초대 토큰이 있으면 재사용한다.
- 유효한 초대 토큰이 없으면 새로 생성한다.
- 새 토큰의 만료 시간은 `party.endedAt`이 있으면 `party.endedAt`이다.
- `party.endedAt`이 없으면 생성 시점 기준 24시간 뒤다.

### Participant

`participant` 테이블은 파티 참여자를 저장한다.

| 필드 | 설명 |
|------|------|
| `party_id` | 참여한 파티 |
| `user_id` | 회원 참여자 ID. 비회원이면 `null` |
| `character_id` | 선택한 캐릭터. 채팅 비허용 파티에서는 `null` |
| `nickname` | 참여자 닉네임 |
| `is_celebrant` | 주인공 여부 |
| `has_written_paper` | 롤링페이퍼 작성 여부 |

회원 중복 참여 방지를 위해 `(party_id, user_id)`에 `uk_participant_party_user` 유니크 제약이 있다. 서비스에서는 사전 조회로 중복을 확인하고, 저장 시점의 동시성 충돌은 `saveAndFlush()` 후 `DataIntegrityViolationException`을 `ALREADY_JOINED`로 매핑한다.

### Character

현재 캐릭터 엔티티는 `avatar` 테이블에 매핑되어 있다.

| 필드 | 설명 |
|------|------|
| `id` | 캐릭터 ID |
| `name` | 캐릭터 이름. `uk_avatar_name` 유니크 제약 |

캐릭터 이미지 URL은 `avatar` 테이블에 중복 저장하지 않고 공통 `image` 테이블을 단일 기준으로 사용한다.

- `Image.targetType = CHARACTER`
- `Image.targetId = character.id`
- `Image.sortOrder = 0`
- `CharacterImageUrlResolver`는 `ImageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(...)`로 대표 이미지를 찾는다.

기본 캐릭터는 애플리케이션 시작 시 `DefaultCharacterInitializer`가 보장한다.

| name | imageUrl |
|------|----------|
| `character1` | `/images/characters/character1.jpg` |
| `character2` | `/images/characters/character2.jpg` |
| `character3` | `/images/characters/character3.jpg` |

이미지 파일은 Spring Boot 정적 리소스 경로인 `src/main/resources/static/images/characters/` 아래에 위치해야 하며, 배포 후 URL은 API 도메인 기준 `/images/characters/character1.jpg` 형태다.

운영 DB에 기존 `avatar.image_url` 컬럼이 남아 있다면 제거하거나 nullable/default 정책을 별도로 정리해야 한다. 코드 기준으로는 더 이상 `Character` insert/update 시 `avatar.image_url`을 쓰지 않는다.

## API 응답 공통 형식

성공 응답은 `ApiResponse<T>`로 감싼다.

```json
{
  "status": 200,
  "data": {}
}
```

에러 응답은 `ErrorResponse` 형식이다.

```json
{
  "status": 400,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지"
  }
}
```

## API 엔드포인트

### 1. 초대링크 활성화

```http
POST /api/v1/parties/{partyId}/invite-link
```

초대 토큰을 생성하거나 기존 유효 토큰을 재사용한다.

#### 인증

- 필수
- Bearer 토큰 필요
- 파티 생성자 또는 이미 참여한 회원만 활성화할 수 있다.

#### Path Variable

| 이름 | 타입 | 설명 |
|------|------|------|
| `partyId` | `Long` | 파티 ID |

#### 응답 200

```json
{
  "status": 200,
  "data": {
    "token": "a1b2c3d4e5f67890"
  }
}
```

#### 에러

| 상황 | ErrorCode | HTTP |
|------|-----------|------|
| 인증 없음 | `AUTH_UNAUTHORIZED` 또는 `UNAUTHORIZED` | 401 |
| 유효하지 않은 토큰 | `AUTH_INVALID_TOKEN` | 401 |
| 파티 없음 | `PARTY_NOT_FOUND` | 404 |
| 활성화 권한 없음 | `PARTY_FORBIDDEN` | 403 |

### 2. 파티 정보 조회

```http
GET /api/v1/parties/{inviteToken}
```

초대 토큰으로 파티 정보를 조회한다.

#### 인증

- 선택사항
- Authorization 헤더가 없으면 비회원 조회로 처리한다.
- 유효한 Authorization 헤더가 있으면 회원 조회로 처리하고, 해당 회원의 기존 참여 정보가 있으면 `myParticipant`에 포함한다.
- 잘못된 Authorization 헤더가 있으면 인증 실패로 처리한다.

#### Path Variable

| 이름 | 타입 | 설명 |
|------|------|------|
| `inviteToken` | `String` | 초대 토큰. 현재 생성 토큰은 16자리 문자열 |

#### 응답 200: 미참여 또는 비회원

```json
{
  "status": 200,
  "data": {
    "name": "생일파티",
    "celebrantNickname": "홍길동",
    "purpose": "BIRTHDAY",
    "option": "REALTIME",
    "startedAt": "2026-04-30T14:30:00",
    "endedAt": null,
    "ended": false,
    "myParticipant": null
  }
}
```

#### 응답 200: 이미 참여한 회원

```json
{
  "status": 200,
  "data": {
    "name": "생일파티",
    "celebrantNickname": "홍길동",
    "purpose": "BIRTHDAY",
    "option": "REALTIME",
    "startedAt": "2026-04-30T14:30:00",
    "endedAt": null,
    "ended": false,
    "myParticipant": {
      "participantId": 10,
      "nickname": "참여자닉네임",
      "characterImageUrl": "/images/characters/character1.jpg"
    }
  }
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | `String?` | 파티 이름 |
| `celebrantNickname` | `String?` | 주인공 닉네임 |
| `purpose` | `PartyPurpose?` | 파티 목적. `BIRTHDAY`, `JOB_CHANGE`, `WEDDING` |
| `option` | `PartyOption?` | 파티 옵션. `REALTIME`, `PAPER_ONLY` |
| `startedAt` | `LocalDateTime?` | 파티 시작 일시 |
| `endedAt` | `LocalDateTime?` | 파티 종료 일시 |
| `ended` | `Boolean` | `endedAt`이 현재 시각보다 과거이거나 현재와 같으면 `true` |
| `myParticipant` | `ParticipantResponse?` | 회원이 이미 참여한 경우 기존 참여 정보. 비회원 또는 미참여 회원은 `null` |

#### 에러

| 상황 | ErrorCode | HTTP |
|------|-----------|------|
| 초대 토큰 없음 | `PARTY_NOT_FOUND` | 404 |
| 초대 토큰 만료 | `INVITE_LINK_EXPIRED` | 400 |
| Authorization 헤더가 잘못됨 | `AUTH_INVALID_TOKEN` 또는 인증 관련 에러 | 401 |
| Authorization 헤더의 회원을 찾을 수 없음 | `AUTH_USER_NOT_FOUND` | 401 |

### 3. 캐릭터 조회: 이번 PR에서 추가할 API

```http
GET /api/v1/characters
```

파티 참여 화면에서 선택 가능한 캐릭터 목록을 조회한다.

현재 코드에는 아직 이 API가 없으므로, 이번 PR에서 추가할 계약이다. 구현 시 기존 `Character`와 `Image` 데이터를 사용해 `characterId`, `name`, `characterImageUrl`을 반환한다.

#### 인증

- 불필요
- 파티 참여 전 화면에서 사용하므로 공개 API로 둔다.

#### 응답 200

```json
{
  "status": 200,
  "data": [
    {
      "characterId": 1,
      "name": "character1",
      "characterImageUrl": "/images/characters/character1.jpg"
    },
    {
      "characterId": 2,
      "name": "character2",
      "characterImageUrl": "/images/characters/character2.jpg"
    },
    {
      "characterId": 3,
      "name": "character3",
      "characterImageUrl": "/images/characters/character3.jpg"
    }
  ]
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `characterId` | `Long` | 파티 참여 요청에 전달할 캐릭터 ID |
| `name` | `String` | 캐릭터 이름 |
| `characterImageUrl` | `String?` | 프론트에서 바로 사용할 이미지 URL |

#### 구현 메모

- `CharacterRepository.findAll()`로 캐릭터 목록을 조회한다.
- 각 캐릭터의 이미지 URL은 `CharacterImageUrlResolver` 또는 같은 기준의 조회 로직을 사용한다.
- 정렬 정책이 필요하다면 우선 `id` 오름차순을 사용한다.
- Swagger 문서와 Controller 테스트를 함께 추가한다.
- 정적 이미지가 브라우저에서 직접 열려야 한다면 `SecurityConfig`에 `/images/**` 공개 허용을 추가해야 한다.

### 4. 파티 참여

```http
POST /api/v1/parties/{inviteToken}/participants
```

초대 토큰으로 파티에 참여한다.

#### 인증

- 선택사항
- Authorization 헤더가 없으면 비회원 참여로 처리한다.
- 유효한 Authorization 헤더가 있으면 회원 참여로 처리하고 `Participant.user`에 연결한다.
- 잘못된 Authorization 헤더가 있으면 인증 실패로 처리한다.

#### Path Variable

| 이름 | 타입 | 설명 |
|------|------|------|
| `inviteToken` | `String` | 초대 토큰 |

#### 요청

```json
{
  "nickname": "참여자닉네임",
  "characterId": 1
}
```

#### 요청 필드

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `nickname` | `String` | 필수 | blank 불가, 최대 20자 | 참여자 닉네임 |
| `characterId` | `Long?` | 조건부 | 값이 있으면 1 이상 | 선택한 캐릭터 ID |

#### characterId 조건

`characterId`는 단순 nullable 필드가 아니라 파티 설정에 따라 허용 여부가 달라진다.

| 파티 조건 | 요청 규칙 | 위반 시 에러 |
|-----------|-----------|--------------|
| `party.isChattingAllow == true` | `characterId` 필수 | `CHARACTER_REQUIRED` |
| `party.isChattingAllow == false` | `characterId` 전달 불가 | `CHARACTER_NOT_ALLOWED` |

현재 `isChattingAllow`는 응답에 내려주지 않는 내부 필드다. 따라서 프론트가 사전에 캐릭터 선택 UI를 제어해야 한다면, 별도 응답 필드 추가 여부를 다시 결정해야 한다.

#### 응답 200: 채팅 허용 파티

```json
{
  "status": 200,
  "data": {
    "participantId": 10,
    "nickname": "참여자닉네임",
    "characterImageUrl": "/images/characters/character1.jpg"
  }
}
```

#### 응답 200: 채팅 비허용 파티

```json
{
  "status": 200,
  "data": {
    "participantId": 11,
    "nickname": "참여자닉네임",
    "characterImageUrl": null
  }
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `participantId` | `Long` | 생성된 참여자 ID |
| `nickname` | `String?` | 참여자 닉네임 |
| `characterImageUrl` | `String?` | 선택한 캐릭터 대표 이미지 URL. 캐릭터가 없으면 `null` |

참여 응답에는 `characterId`를 반환하지 않는다. 프론트가 참여 완료 후 바로 이미지를 표시할 수 있도록 `characterImageUrl`만 반환한다.

#### 에러

| 상황 | ErrorCode | HTTP |
|------|-----------|------|
| 초대 토큰 없음 | `PARTY_NOT_FOUND` | 404 |
| 초대 토큰 만료 | `INVITE_LINK_EXPIRED` | 400 |
| 종료된 파티 | `PARTY_ENDED` | 400 |
| 회원 중복 참여 | `ALREADY_JOINED` | 409 |
| 존재하지 않는 캐릭터 | `CHARACTER_NOT_FOUND` | 404 |
| 채팅 허용 파티에서 `characterId` 누락 | `CHARACTER_REQUIRED` | 400 |
| 채팅 비허용 파티에서 `characterId` 전달 | `CHARACTER_NOT_ALLOWED` | 400 |
| `nickname` blank 또는 20자 초과 | `INVALID_INPUT` 계열 validation 응답 | 400 |
| `characterId <= 0` | `INVALID_INPUT` 계열 validation 응답 | 400 |
| Authorization 헤더가 잘못됨 | `AUTH_INVALID_TOKEN` 또는 인증 관련 에러 | 401 |
| Authorization 헤더의 회원을 찾을 수 없음 | `AUTH_USER_NOT_FOUND` | 401 |

## 비즈니스 로직 상세

### 초대링크 활성화

1. `partyId`로 파티를 조회한다.
2. 파티가 없으면 `PARTY_NOT_FOUND`.
3. 요청 회원이 파티 생성자이거나 해당 파티 참여자인지 확인한다.
4. 권한이 없으면 `PARTY_FORBIDDEN`.
5. 현재 시각 이후에 만료되는 기존 초대 토큰이 있으면 재사용한다.
6. 없으면 새 `PartyInvite`를 생성한다.
7. 응답에는 전체 링크가 아니라 `token`만 반환한다.

### 파티 정보 조회

1. `inviteToken`으로 `PartyInvite`를 조회한다.
2. 토큰이 없으면 `PARTY_NOT_FOUND`.
3. `expiresAt`이 현재 시각보다 이후가 아니면 `INVITE_LINK_EXPIRED`.
4. 토큰의 `party`로 파티 정보를 구성한다.
5. 인증 회원이면 `party + user`로 기존 참여자를 조회한다.
6. 기존 참여자가 있으면 `myParticipant`에 `ParticipantResponse`를 넣는다.
7. `ended`는 `endedAt != null && !endedAt.isAfter(now)` 기준으로 계산한다.
8. 종료된 파티여도 조회 응답은 반환한다.

### 파티 참여

1. `inviteToken`으로 유효한 `PartyInvite`를 조회한다.
2. 토큰이 없으면 `PARTY_NOT_FOUND`.
3. 토큰이 만료됐으면 `INVITE_LINK_EXPIRED`.
4. `party.endedAt`이 현재 시각보다 이후가 아니면 `PARTY_ENDED`.
5. 인증 회원이면 `userId`로 회원을 조회한다.
6. 회원을 찾을 수 없으면 `AUTH_USER_NOT_FOUND`.
7. 회원이 이미 같은 파티에 참여했다면 `ALREADY_JOINED`.
8. `isChattingAllow`와 `characterId` 조건을 검증한다.
9. `characterId`가 있으면 캐릭터를 조회한다.
10. 캐릭터가 없으면 `CHARACTER_NOT_FOUND`.
11. `Participant`를 저장하고 즉시 flush한다.
12. 유니크 제약 충돌이 `uk_participant_party_user`이면 `ALREADY_JOINED`.
13. 저장된 참여자와 캐릭터 이미지 URL로 `ParticipantResponse`를 반환한다.

## Security 설정

현재 공개 경로는 메서드와 경로를 좁혀서 허용한다.

```kotlin
auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*").permitAll()
auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/participants").permitAll()
```

초대링크 활성화와 파티 생성은 인증이 필요하다.

```http
POST /api/v1/parties/{partyId}/invite-link
POST /api/v1/parties/{partyOption}
```

이번 PR에서 캐릭터 조회 API를 추가하면 다음 공개 허용이 필요하다.

```kotlin
auth.requestMatchers(HttpMethod.GET, "/api/v1/characters").permitAll()
```

캐릭터 이미지 파일을 브라우저에서 직접 열어야 한다면 다음 공개 허용도 필요하다.

```kotlin
auth.requestMatchers(HttpMethod.GET, "/images/**").permitAll()
```

## 프론트엔드 연동 기준

### 초대 토큰 사용

초대링크 활성화 응답은 다음처럼 토큰만 내려온다.

```json
{
  "status": 200,
  "data": {
    "token": "a1b2c3d4e5f67890"
  }
}
```

프론트는 이 토큰으로 사용자에게 공유할 URL을 구성한다. 서버의 조회/참여 API에는 같은 토큰을 path variable로 전달한다.

### 파티 정보 화면

- `GET /api/v1/parties/{inviteToken}` 호출
- `ended == true`이면 참여 CTA를 막거나 종료된 파티 화면을 보여준다.
- `myParticipant != null`이면 이미 참여한 회원이므로 참여 폼을 건너뛰고 기존 참여 정보로 진입할 수 있다.
- `myParticipant.characterImageUrl`이 있으면 해당 이미지를 바로 표시한다.

### 캐릭터 선택 화면

- 이번 PR에서 `GET /api/v1/characters`를 추가한 뒤, 참여 폼 진입 시 캐릭터 목록을 조회한다.
- 참여 요청에는 캐릭터 이미지 URL이 아니라 `characterId`를 보낸다.
- 참여 성공 후에는 응답의 `characterImageUrl`을 사용한다.

### 파티 참여 요청

채팅 허용 파티 예시:

```json
{
  "nickname": "참여자닉네임",
  "characterId": 1
}
```

채팅 비허용 파티 예시:

```json
{
  "nickname": "참여자닉네임"
}
```

## 테스트 전략

### 서비스 테스트

- 유효한 초대 토큰으로 참여 성공
- 만료된 초대 토큰이면 `INVITE_LINK_EXPIRED`
- 없는 초대 토큰이면 `PARTY_NOT_FOUND`
- 종료된 파티면 `PARTY_ENDED`
- 회원 중복 참여면 `ALREADY_JOINED`
- DB 유니크 제약 충돌도 `ALREADY_JOINED`
- 없는 캐릭터면 `CHARACTER_NOT_FOUND`
- 채팅 허용 파티에서 `characterId` 없으면 `CHARACTER_REQUIRED`
- 채팅 비허용 파티에서 `characterId` 있으면 `CHARACTER_NOT_ALLOWED`
- 채팅 비허용 파티에서 `characterId` 없이 참여 성공
- 참여 응답에 `characterImageUrl` 포함

### 컨트롤러 테스트

- 비회원 파티 정보 조회 성공
- 회원 파티 정보 조회 시 `myParticipant` 포함
- 비회원 파티 참여 성공
- 회원 파티 참여 성공
- 종료된 파티 참여 실패
- 중복 참여 실패
- validation 실패
- 존재하지 않는 캐릭터 실패
- 캐릭터 선택 규칙 실패
- 캐릭터 조회 API 추가 후 목록 조회 성공

### 초기 데이터 테스트

- 기본 캐릭터 3개가 생성된다.
- 기존 캐릭터가 있으면 중복 생성하지 않는다.
- 각 캐릭터의 `ImageTargetType.CHARACTER` 이미지가 생성된다.
- 이미지 URL은 `/images/characters/character{n}.jpg` 형식을 유지한다.

## 이번 PR 체크리스트

- [ ] 캐릭터 조회 API 추가
- [ ] 캐릭터 조회 응답 DTO 추가
- [ ] 캐릭터 조회 Swagger 문서 추가
- [ ] `GET /api/v1/characters` 공개 허용 추가
- [ ] `/images/**` 공개 허용 필요 여부 확인 후 반영
- [ ] 파티 참여 응답이 `characterImageUrl`만 반환하는지 확인
- [ ] 파티 정보 조회의 `myParticipant`도 같은 `ParticipantResponse` 계약을 사용하는지 확인
- [ ] 문서와 Swagger 예시의 `PartyOption` 값이 실제 enum(`REALTIME`, `PAPER_ONLY`)과 일치하는지 확인
- [ ] 관련 Controller/Service 테스트 추가 또는 갱신
