# 실시간 파티 채팅 기능 설계

## 개요

`REALTIME` 타입 파티에서 주최자와 링크 참여자들이 실시간으로 채팅할 수 있는 기능을 구현한다.
로그인 여부와 관계없이 파티 소속 참여자라면 채팅 가능하다.
메시지 전송은 REST POST, 수신은 SSE(Server-Sent Events)로 처리한다.

## 채팅 활성화 조건

| 파티 상태 | 채팅 전송 | 채팅 구독 |
|-----------|----------|----------|
| `ROLLING_PAPER_OPEN` | 불가 | 불가 |
| `LIVE_OPEN` | 가능 | 가능 |
| `LIVE_CLOSED` | 불가 | 불가 |
| `ROLLING_PAPER_CLOSED` | 불가 | 불가 |

- `PAPER_ONLY` 파티는 채팅 기능 없음
- `RealtimeParticipantProfile`이 있는 참여자만 채팅 가능

## 인증 방식

채팅 API는 두 가지 인증을 모두 지원한다.

| 사용자 유형 | 인증 수단 |
|------------|----------|
| 로그인 사용자 | `Authorization: Bearer {jwt}` |
| 비로그인 사용자 | `X-Participant-Token: {participantToken}` |

두 헤더가 모두 없으면 `UNAUTHORIZED` 에러.

## API

### 1. 라이브 입장 (닉네임 + 캐릭터 선택)

```
POST /api/v1/party-invites/{inviteToken}/realtime-participants
Content-Type: application/json
(인증 불필요)

{
  "nickname": "토끼왕",
  "characterId": 3
}
```

응답 (201 Created):
```json
{
  "status": 201,
  "data": {
    "participantToken": "a1b2c3d4e5f6g7h8"
  }
}
```

- `RealtimeParticipantProfile`을 생성하고 UUID `participantToken`을 발급
- 이미 프로필이 있으면 닉네임 + 캐릭터를 업데이트하고 기존 토큰 반환
- 주최자도 이 API로 캐릭터를 선택할 수 있음
- 비로그인 사용자는 익명 `Participant` 생성 후 프로필 생성

### 2. 메시지 전송

```
POST /api/v1/parties/{partyId}/chat-messages
Authorization: Bearer {jwt}  또는  X-Participant-Token: {token}
Content-Type: application/json

{
  "content": "안녕하세요!"
}
```

응답 (201 Created):
```json
{
  "status": 201,
  "data": {
    "messageId": 1,
    "content": "안녕하세요!",
    "senderNickname": "토끼왕",
    "senderCharacterId": 3,
    "sentAt": "2026-05-06T14:30:00"
  }
}
```

> `senderCharacterId`는 nullable.

### 3. SSE 구독

```
GET /api/v1/parties/{partyId}/chat-messages/stream
Authorization: Bearer {jwt}  또는  X-Participant-Token: {token}
Accept: text/event-stream
```

접속 즉시 `history` 이벤트로 과거 메시지 전체를 전송한 뒤,
이후 신규 메시지는 `message` 이벤트로 실시간 push된다.

```
event: history
data: [{"messageId":1,"content":"...","senderNickname":"...","senderCharacterId":3,"sentAt":"..."}]

event: message
data: {"messageId":2,"content":"...","senderNickname":"...","senderCharacterId":3,"sentAt":"..."}
```

## 컴포넌트 구조

```
chat/
  controller/
    ChatApi.kt
    ChatController.kt
  dto/
    EnterRealtimePartyRequest.kt
    EnterRealtimePartyResponse.kt
    SendChatMessageRequest.kt
    ChatMessageResponse.kt
  service/
    SseEmitterRegistry.kt
  usecase/
    EnterRealtimePartyUseCase.kt
    SendChatMessageUseCase.kt
    SubscribeChatUseCase.kt
```

기존 파일 수정:
- `RealtimeParticipantProfile` — `participantToken: String` (UUID) 필드 추가
- `RealtimeParticipantProfileRepository` — 토큰 조회 메서드 추가
- `ChatMessageRepository` — `findAllByPartyIdOrderByCreatedAtAsc` 추가
- `ErrorCode` — 채팅 에러코드 추가
- `SecurityConfig` — 라이브 입장 엔드포인트 `permitAll` 추가

## SseEmitterRegistry

```
ConcurrentHashMap<partyId: Long, CopyOnWriteArrayList<SseEmitter>>
```

- `subscribe(partyId, emitter)`: 등록. timeout/completion/error 콜백에서 자동 해제
- `broadcast(partyId, event)`: 전체 emitter에 push. 실패한 emitter는 즉시 제거

## 검증 흐름

### 라이브 입장

1. inviteToken으로 Party 조회 → 없으면 `PARTY_NOT_FOUND`
2. `REALTIME` 타입 확인 → 아니면 `CHAT_NOT_SUPPORTED`
3. 초대링크 만료 확인 → 만료면 `INVITE_LINK_EXPIRED`
4. userId 있으면 User로 Participant 조회/생성, 없으면 익명 Participant 생성
5. Participant의 RealtimeParticipantProfile 조회
   - 없으면: nickname + character로 새 Profile 생성 + UUID participantToken 발급
   - 있으면: nickname + character 업데이트 + 기존 participantToken 반환
6. `EnterRealtimePartyResponse(participantToken)` 반환

### 참여자 식별 (채팅 공통)

```kotlin
fun resolveProfile(
    userId: Long?,
    participantToken: String?,
    partyId: Long
): RealtimeParticipantProfile
```

1. JWT 있으면 userId + partyId → Participant → Profile 조회
2. participantToken 있으면 토큰으로 Profile 직접 조회
3. 둘 다 없으면 `UNAUTHORIZED`
4. Profile 없으면 `CHARACTER_REQUIRED`

### 메시지 전송

1. Party 조회 → 없으면 `PARTY_NOT_FOUND`
2. `REALTIME` 타입 확인 → 아니면 `CHAT_NOT_SUPPORTED`
3. `RealtimeParty.status() == LIVE_OPEN` 확인 → 아니면 `CHAT_NOT_ACTIVE`
4. 참여자 식별 (위 공통 로직)
5. Profile의 partyId가 요청 partyId와 일치하는지 확인 → 아니면 `PARTY_FORBIDDEN`
6. `ChatMessage` 저장
7. `SseEmitterRegistry.broadcast()`

### SSE 구독

1. Party 조회 → 없으면 `PARTY_NOT_FOUND`
2. `REALTIME` 타입 확인 → 아니면 `CHAT_NOT_SUPPORTED`
3. 참여자 식별 (위 공통 로직)
4. Profile의 partyId가 요청 partyId와 일치하는지 확인 → 아니면 `PARTY_FORBIDDEN`
5. `SseEmitter` 생성 (timeout: 15분)
6. DB에서 과거 메시지 조회 → `history` 이벤트 전송
7. Registry에 emitter 등록

## 에러코드 추가

| 코드 | HTTP 상태 | 메시지 |
|------|----------|--------|
| `CHAT_NOT_SUPPORTED` | 400 | 채팅을 지원하지 않는 파티입니다 |
| `CHAT_NOT_ACTIVE` | 400 | 현재 채팅이 활성화된 시간이 아닙니다 |

기존 재사용:
- `CHARACTER_REQUIRED` — 프로필 없는 참여자가 채팅 시도 시
- `UNAUTHORIZED` — 인증 수단 없음
- `PARTY_FORBIDDEN` — 다른 파티의 토큰 사용 시

## 테스트 범위

- `EnterRealtimePartyUseCase`: 비로그인 입장 성공, 로그인 입장 성공, 재입장 시 토큰 재사용, PAPER_ONLY 파티 실패, 만료 초대링크 실패
- `SendChatMessageUseCase`: JWT로 전송 성공, participantToken으로 전송 성공, LIVE_OPEN 외 실패, 비참여자 실패, PAPER_ONLY 실패
- `SubscribeChatUseCase`: 구독 성공 + 히스토리 포함, JWT/token 양방향 확인, 비참여자 실패
- `SseEmitterRegistry`: 브로드캐스트, dead emitter 자동 제거

## 비고

- SSE timeout: 15분 (`15 * 60 * 1000L`)
- Java 25 가상 스레드 환경으로 SSE 연결 비용 낮음
- 단일 서버 환경 가정
- `participantToken`은 UUID v4, `RealtimeParticipantProfile`에 저장
- 익명 Participant는 `user = null`로 생성 (기존 패턴과 동일)
