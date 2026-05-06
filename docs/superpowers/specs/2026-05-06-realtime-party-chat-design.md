# 실시간 파티 채팅 기능 설계

## 개요

`REALTIME` 타입 파티에서 주최자와 링크 참여자들이 실시간으로 채팅할 수 있는 기능을 구현한다.
메시지 전송은 REST POST, 수신은 SSE(Server-Sent Events)로 처리한다.

## 채팅 활성화 조건

| 파티 상태 | 채팅 전송 | 채팅 구독 |
|-----------|----------|----------|
| `ROLLING_PAPER_OPEN` | 불가 | 불가 |
| `LIVE_OPEN` | 가능 | 가능 |
| `LIVE_CLOSED` | 불가 | 불가 |
| `ROLLING_PAPER_CLOSED` | 불가 | 불가 |

- `PAPER_ONLY` 파티는 채팅 기능 자체가 없음
- 참여자(Participant)로 등록된 사용자만 채팅 가능

## API

### 메시지 전송

```
POST /api/v1/parties/{partyId}/chat-messages
Authorization: Bearer {token}
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

> `senderCharacterId`는 nullable. 캐릭터를 선택하지 않은 주최자의 경우 null일 수 있음.

### SSE 구독

```
GET /api/v1/parties/{partyId}/chat-messages/stream
Authorization: Bearer {token}
Accept: text/event-stream
```

접속 즉시 `history` 이벤트로 과거 메시지 전체를 전송한 뒤,
이후 신규 메시지는 `message` 이벤트로 실시간 push된다.

SSE 이벤트 형식:
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
    ChatApi.kt                  Swagger 인터페이스
    ChatController.kt           REST + SSE 엔드포인트
  dto/
    SendChatMessageRequest.kt   메시지 전송 요청
    ChatMessageResponse.kt      메시지 응답 DTO
  service/
    SseEmitterRegistry.kt       emitter 등록/해제/브로드캐스트
  usecase/
    SendChatMessageUseCase.kt   전송 검증 + 저장 + 브로드캐스트
    SubscribeChatUseCase.kt     구독 검증 + 히스토리 전송 + emitter 등록
```

기존 파일 수정:
- `ChatMessageRepository` — `findAllByPartyIdOrderByCreatedAtAsc` 추가
- `ErrorCode` — 채팅 에러코드 2개 추가
- `PartyRepository` — `findPartyById` 재사용 (수정 없음)

## SseEmitterRegistry

```
ConcurrentHashMap<partyId: Long, CopyOnWriteArrayList<SseEmitter>>
```

- `subscribe(partyId, emitter)`: emitter 등록. timeout/completion/error 콜백에서 자동 해제
- `broadcast(partyId, event)`: partyId의 모든 emitter에 push. 전송 실패한 dead emitter는 즉시 제거

## 검증 흐름

### 메시지 전송

1. Party 조회 → 없으면 `PARTY_NOT_FOUND`
2. `REALTIME` 타입 확인 → 아니면 `CHAT_NOT_SUPPORTED`
3. `RealtimeParty.status() == LIVE_OPEN` 확인 → 아니면 `CHAT_NOT_ACTIVE`
4. `Participant` 존재 확인 (userId + partyId) → 없으면 `PARTY_FORBIDDEN`
5. `RealtimeParticipantProfile` 존재 확인 → 없으면 `CHARACTER_REQUIRED`
6. `ChatMessage` 저장
7. `SseEmitterRegistry.broadcast()`

### SSE 구독

1. Party 조회 → 없으면 `PARTY_NOT_FOUND`
2. `REALTIME` 타입 확인 → 아니면 `CHAT_NOT_SUPPORTED`
3. `Participant` 존재 확인 → 없으면 `PARTY_FORBIDDEN`
4. `RealtimeParticipantProfile` 존재 확인 → 없으면 `CHARACTER_REQUIRED`
5. `SseEmitter` 생성 (timeout: 15분)
6. DB에서 과거 메시지 조회 → `history` 이벤트 전송
7. Registry에 emitter 등록

> 링크 참여자의 경우 `RealtimeParticipantProfile`은 별도의 "라이브 입장" 플로우에서 생성됨.
> 해당 플로우 미구현 시 링크 참여자는 채팅 불가 (CHARACTER_REQUIRED 응답).

## 에러코드 추가

| 코드 | HTTP 상태 | 메시지 |
|------|----------|--------|
| `CHAT_NOT_SUPPORTED` | 400 Bad Request | 채팅을 지원하지 않는 파티입니다 |
| `CHAT_NOT_ACTIVE` | 400 Bad Request | 현재 채팅이 활성화된 시간이 아닙니다 |

기존 에러코드 재사용:
- `CHARACTER_REQUIRED` — 프로필 없는 참여자가 채팅 시도 시

## 테스트 범위

- `SendChatMessageUseCase`: LIVE_OPEN 전송 성공, LIVE_OPEN 외 전송 실패, 비참여자 전송 실패, PAPER_ONLY 파티 전송 실패, 프로필 없는 참여자 전송 실패
- `SubscribeChatUseCase`: 구독 성공 + 히스토리 포함 확인, 비참여자 구독 실패, PAPER_ONLY 파티 구독 실패, 프로필 없는 참여자 구독 실패
- `SseEmitterRegistry`: 브로드캐스트, dead emitter 자동 제거

## 비고

- SSE 연결 timeout은 15분 (LIVE_OPEN 10분 + 여유 5분)
- Java 25 가상 스레드 환경이므로 SSE 연결 유지 시 스레드 비용 낮음
- 단일 서버 환경 가정 (멀티 인스턴스 대응 불필요)
- `SseEmitter.DEFAULT_TIMEOUT`을 사용하지 않고 명시적으로 `15 * 60 * 1000L` 지정
