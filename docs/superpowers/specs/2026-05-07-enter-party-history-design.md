# 실시간 파티 채팅 설계

## 개요

실시간 파티(`REALTIME`) 참여자가 닉네임과 캐릭터를 선택해 입장하고, SSE 스트림을 통해 실시간으로 채팅하는 기능이다. 입장과 SSE 구독은 단일 엔드포인트에서 처리된다. 중간 입장자도 기존 채팅 내역을 즉시 확인할 수 있다.

---

## 전체 비즈니스 흐름

```
참여자                           서버
  │                               │
  │── POST /invites/{token}/      │
  │   realtime-participants/stream─►│  1. 초대 토큰 검증
  │                               │  2. Participant 생성 또는 재사용
  │                               │  3. Profile upsert (닉네임·캐릭터)
  │                               │  4. SseEmitter 등록
  │                               │  5. 기존 채팅 내역 조회
  │◄── event: entered {           │
  │     participantToken,         │
  │     messages: [...] } ────────│
  │                               │
  │── POST /parties/{id}/         │
  │   chat-messages ──────────────►│  6. 파티 상태 검증 (LIVE_OPEN 만)
  │                               │  7. ChatMessage DB 저장 (트랜잭션)
  │◄── { messageId, content, ... }│  8. 트랜잭션 커밋
  │                               │  9. AFTER_COMMIT → SSE 브로드캐스트
  │◄── event: message { ... } ────│  (SSE 구독 중인 모든 참여자 수신)
```

---

## API 명세

### 1. 실시간 파티 입장 + SSE 구독

**`POST /api/v1/party-invites/{inviteToken}/realtime-participants/stream`**

인증 불필요. 응답 Content-Type: `text/event-stream`.

#### 요청

```json
{
  "nickname": "토끼왕",
  "characterId": 1
}
```

#### 검증 순서

1. 초대 토큰 존재 여부 → 없으면 `PARTY_NOT_FOUND (404)`
2. `partyOption == REALTIME` → 아니면 `CHAT_NOT_SUPPORTED (400)`
3. 초대 링크 만료(`expiresAt`) → 만료 시 `INVITE_LINK_EXPIRED (400)`
4. 입장 가능 시간 → 파티 시작 `ENTERABLE_BEFORE_MINUTES`(5분) 전부터 허용, 그 이전이면 `CHAT_NOT_ACTIVE (400)`
5. 캐릭터 존재 여부 → 없으면 `CHARACTER_NOT_FOUND (404)`

#### Participant 처리

- 비로그인: 매번 새로운 익명 `Participant` 생성
- 로그인: 파티 내 동일 userId의 `Participant`가 있으면 재사용, 없으면 생성

#### Profile upsert

- `Participant`에 연결된 `RealtimeParticipantProfile`이 이미 있으면 닉네임·캐릭터를 덮어쓰고 기존 `participantToken` 유지 (재입장 시 토큰 재사용)
- 없으면 새로 생성, `participantToken`은 8자리 랜덤 영숫자 생성

#### SSE 이벤트

SseEmitter 등록 후 첫 이벤트로 `entered`를 전송하고, 연결은 **15분** 후 자동 종료.

| 이벤트 이름 | 발생 시점 | 데이터 |
|---|---|---|
| `entered` | 입장 직후 1회 | `{ participantToken, messages: [...] }` |
| `message` | 누군가 메시지 전송 시 | 단건 메시지 객체 |

```
event: entered
data: {"participantToken":"abc12345","messages":[{"messageId":1,"content":"먼저 입장한 사람의 메시지","senderNickname":"토끼","senderCharacterId":2,"sentAt":"..."}]}

event: message
data: {"messageId":2,"content":"반가워요","senderNickname":"곰","senderCharacterId":1,"sentAt":"..."}
```

- `messages`가 없으면 빈 배열 `[]`
- `participantToken`은 이후 인증 수단으로 사용 (비로그인 참여자)

---

### 2. 채팅 메시지 전송

**`POST /api/v1/parties/{partyId}/chat-messages`**

인증: `Authorization: Bearer {token}` 또는 `X-Participant-Token: {participantToken}` 헤더 중 하나 필수.

#### 요청

```json
{
  "content": "안녕하세요!"
}
```

#### 검증

1. 파티 `REALTIME` 여부
2. 파티 상태가 `LIVE_OPEN`인지 확인 — 파티 시작 전(`ROLLING_PAPER_OPEN`)이나 종료 후에는 `CHAT_NOT_ACTIVE (400)`
3. 발신자 Profile 조회 (파티 소속 여부 포함)

#### 전송 흐름

1. `ChatMessage` DB 저장 (`@Transactional`)
2. 응답 DTO 구성 후 `ChatMessageBroadcastEvent` 발행
3. **트랜잭션 커밋 완료 후** (`@TransactionalEventListener(AFTER_COMMIT)`) SSE 브로드캐스트 실행

> 트랜잭션 커밋 전에 브로드캐스트하면 클라이언트가 메시지를 수신했는데 DB 롤백이 발생할 수 있다. `AFTER_COMMIT`으로 이 타이밍 문제를 방지한다.

---

## 스키마

### chat_message

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| content | VARCHAR(1000) | 메시지 내용 |
| party_id | BIGINT FK | 파티 |
| profile_id | BIGINT FK | 발신자 프로필 |
| created_at | DATETIME | 전송 시각 |

### realtime_participant_profile

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| participant_id | BIGINT FK | |
| nickname | VARCHAR | 표시 닉네임 |
| character_id | BIGINT FK NULL | 선택한 캐릭터 |
| participant_token | VARCHAR(8) UNIQUE | 비로그인 인증 토큰 |

---

## 클라이언트 권장 시퀀스

```
1. POST /invites/{token}/realtime-participants/stream (SSE 연결)
   → entered 이벤트: participantToken 저장, messages로 화면 초기 렌더링
   → 이후 message 이벤트를 실시간으로 화면에 추가

2. 채팅 입력 시 POST /parties/{id}/chat-messages
   → SSE로 message 이벤트 수신
```
