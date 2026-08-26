# WebSocket(STOMP) 채팅 API

실시간 파티 채팅의 WebSocket 버전 프로토콜 문서입니다. Swagger(OpenAPI)는 HTTP 엔드포인트만 문서화하므로 `@MessageMapping` 기반의 이 API는 Swagger UI에 나타나지 않습니다 — 이 문서가 그 대체입니다.

같은 기능의 SSE 버전(`/api/v1/party-invites/{inviteToken}/realtime-participants/stream` 등)은 Swagger에 그대로 남아 있고, 당분간 WebSocket과 함께 운영됩니다. 두 방식 중 하나만 골라 붙이면 됩니다 — 섞어 쓰지 마세요(participantToken은 두 방식이 공유하지만, 입장 이후의 이벤트 흐름은 붙인 방식으로만 옵니다).

## 개요

- 엔드포인트: `ws://{host}/ws` (SockJS 미사용, 순정 WebSocket)
- 프로토콜: STOMP 1.1+
- 브로커 prefix: `/topic` (구독), 앱 prefix: `/app` (전송)
- 클라이언트 라이브러리 예: [`@stomp/stompjs`](https://github.com/stomp-js/stompjs)

## 연결 (CONNECT)

STOMP CONNECT 프레임에 `Authorization` 헤더를 실어 인증 여부를 결정합니다. SSE의 `Authorization: Bearer {token}` / `X-Participant-Token`과 동일한 모델입니다.

| 상황 | 동작 |
|---|---|
| `Authorization` 헤더 없음 | 게스트로 연결 (participantToken만으로 신원 식별) |
| `Authorization: Bearer {유효한 JWT}` | 로그인 사용자로 연결. **본인이 파티 주최자인 경우**, participantToken 없이 최초 입장해도 CELEBRANT로 인식됩니다. 단 이때 `nickname`은 파티 생성 시 등록된 주최자 닉네임과 **동일해야** 합니다 — 다르면 `PARTY_HOST_NICKNAME_NOT_EDITABLE` 에러가 옵니다 |
| `Authorization: Bearer {유효하지 않은/만료된 JWT}` | CONNECT 자체가 거부되고 연결이 끊깁니다 |

```js
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${jwt}` }, // 로그인 사용자만, 게스트는 생략
});
```

## 요청 목적지 (SEND)

모든 요청 페이로드는 `clientRequestId`(임의의 고유 문자열, UUID 권장)를 포함해야 합니다. 서버가 이 값으로 개인 ack/에러를 돌려줍니다.

### 1. 입장 + 구독 — `/app/party-invites/{inviteToken}/realtime-participants`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `nickname` | string | Y | 공백 불가, 최대 20자 |
| `characterId` | number | Y | |
| `participantToken` | string | N | 재입장 시에만 포함 |
| `clientRequestId` | string | Y | |

```json
{ "nickname": "토끼왕", "characterId": 1, "clientRequestId": "c1b2..." }
```

입장에 성공하면 서버가 자동으로 해당 세션에 파티 브로드캐스트 구독 권한을 부여합니다(아래 "구독 목적지" 참고) — 클라이언트가 별도로 요청할 필요는 없습니다.

### 2. 메시지 전송 — `/app/parties/{partyId}/chat-messages`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `content` | string | Y | 공백 불가, 최대 1000자 |
| `participantToken` | string | Y | 입장 시 받은 토큰 |
| `clientRequestId` | string | Y | |

```json
{ "content": "안녕하세요!", "participantToken": "aB3xZ9qR", "clientRequestId": "c1b2..." }
```

파티가 `LIVE_OPEN` 상태일 때만 전송됩니다.

### 3. 퇴장 — `/app/parties/{partyId}/leave`

| 필드 | 타입 | 필수 |
|---|---|---|
| `participantToken` | string | Y |
| `clientRequestId` | string | Y |

```json
{ "participantToken": "aB3xZ9qR", "clientRequestId": "c1b2..." }
```

### 4. 촛불 끄기 — `/app/parties/{partyId}/candle-blow/candles/{candleId}`

`candleId`(1~9)는 목적지 경로에 포함합니다.

| 필드 | 타입 | 필수 |
|---|---|---|
| `participantToken` | string | Y |
| `clientRequestId` | string | Y |

```json
{ "participantToken": "aB3xZ9qR", "clientRequestId": "c1b2..." }
```

촛불끄기 세션이 아직 시작되지 않았으면 `CANDLE_BLOW_NOT_STARTED` 에러가 옵니다. 이미 꺼진 촛불을 다시 꺼도 에러 없이 현재 상태를 그대로 돌려줍니다.

### 5. 박터뜨리기 탭 제출 — `/app/parties/{partyId}/burst-game/taps`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `tapCount` | number | Y | 1~30 (이번 batch에 포함된 터치 수) |
| `clientSequence` | number | Y | 참가자별 증가하는 batch 멱등성 키. 이미 처리한 값은 무시됩니다 |
| `participantToken` | string | Y | |
| `clientRequestId` | string | Y | |

```json
{ "tapCount": 7, "clientSequence": 12, "participantToken": "aB3xZ9qR", "clientRequestId": "c1b2..." }
```

라운드 시작 전/종료 후 제출은 에러가 아니라 **200 ack로 `accepted: false`** 를 돌려줍니다(아래 "tap-submitted" 참고). 박터뜨리기 라운드 자체는 유저 요청이 아니라 파티 진행 단계 전환(`party-phase-changed`)이 트리거하므로 별도의 "시작" 요청 목적지는 없습니다.

### 6. 폭죽 트리거 — `/app/parties/{partyId}/fireworks`

| 필드 | 타입 | 필수 |
|---|---|---|
| `participantToken` | string | Y |
| `clientRequestId` | string | Y |

```json
{ "participantToken": "aB3xZ9qR", "clientRequestId": "c1b2..." }
```

## 구독 목적지 (SUBSCRIBE)

| 목적지 | 용도 | 구독 시점 |
|---|---|---|
| `/topic/parties/{partyId}/personal/{clientRequestId}` | 내가 보낸 요청 하나에 대한 개인 응답(성공 ack) | 요청을 **보내기 전에** 미리 구독 — 응답이 구독보다 먼저 도착하면 유실됩니다 |
| `/topic/errors/{clientRequestId}` | 내가 보낸 요청 하나에 대한 실패 통지 | 위와 동일하게 요청 전에 미리 구독 |
| `/topic/parties/{partyId}` | 파티 전체 브로드캐스트(다른 참가자의 입장/퇴장/메시지) | 입장에 **성공한 뒤에만** 구독 가능. 미입장 상태로 구독하거나 와일드카드(`/topic/parties/**` 등)로 구독하면 즉시 ERROR 프레임과 함께 연결이 거부됩니다 |

## 이벤트 (수신 페이로드)

모든 수신 프레임은 `{ "event": "이벤트이름", "data": {...} }` 형태로 감싸져 옵니다.

### 개인 ack (`/topic/parties/{partyId}/personal/{clientRequestId}`)

| `event` | 어느 요청의 응답인가 | `data` 구조 |
|---|---|---|
| `party-state` | 입장 | 아래 "party-state" 참고 |
| `entered` | 입장 | `{ participantToken, messages: ChatMessageResponse[] }` |
| `message-sent` | 메시지 전송 | `ChatMessageResponse` (아래 참고) |
| `left` | 퇴장 | `{ nickname, role }` |
| `candle-blown` | 촛불 끄기 | `CandleBlowResponse` (아래 참고) |
| `tap-submitted` | 탭 제출 | `SubmitBurstGameTapResponse` (아래 참고) |
| `fireworks-triggered` | 폭죽 트리거 | `{ partyId, participantId, nickname }` |

입장 시 `party-state`와 `entered` 두 프레임이 **순서대로** 각각 별도 전송됩니다.

**party-state**
```json
{
  "partyId": 1,
  "status": "LIVE_OPEN",
  "liveStartAt": "2026-05-19T20:00:00",
  "endingStartedAt": null,
  "endedAt": "2026-05-19T20:11:00",
  "endingReason": null,
  "hostNickname": "홍길동",
  "hostFarewellAvailable": false,
  "hostFarewellAvailableAt": null,
  "serverNow": "2026-05-19T20:03:00"
}
```
`status`는 `ROLLING_PAPER_OPEN | LIVE_OPEN | LIVE_ENDING | LIVE_CLOSED | ROLLING_PAPER_CLOSED` 중 하나입니다.

**entered**
```json
{
  "participantToken": "aB3xZ9qR",
  "messages": [
    {
      "messageId": 1,
      "content": "안녕하세요!",
      "senderNickname": "토끼왕",
      "senderCharacterId": 2,
      "senderCharacterImageUrl": "https://example.com/rabbit.png",
      "senderRole": "PARTICIPANT",
      "sentAt": "2026-05-07T10:00:00"
    }
  ]
}
```

**CandleBlowResponse** (`candle-blown` ack, `candle-blow-started`/`-progress`/`-ended` 브로드캐스트가 공유하는 구조)
```json
{
  "partyId": 1,
  "status": "ACTIVE",
  "candles": [
    { "candleId": 1, "extinguished": false },
    { "candleId": 2, "extinguished": true }
  ],
  "finishedReason": null
}
```
`status`는 `WAITING | ACTIVE | FINISHED`, `finishedReason`은 `ALL_EXTINGUISHED | TIMEOUT`(종료 전에는 `null`)입니다. `candles`는 항상 9개(1~9)가 옵니다.

**SubmitBurstGameTapResponse** (`tap-submitted` ack)
```json
{
  "partyId": 1,
  "myParticipantId": 37,
  "accepted": true,
  "ignoredReason": null,
  "myTapCount": 11,
  "totalTapCount": 137,
  "stateVersion": 13,
  "serverTime": "2026-05-14T20:10:07.120",
  "rankings": [
    { "rank": 1, "participantId": 37, "nickname": "토끼왕", "characterId": 2, "characterThumbnailImageUrl": "https://example.com/rabbit.png", "role": "PARTICIPANT", "tapCount": 11 }
  ]
}
```
`ignoredReason`은 `accepted: false`일 때만 채워집니다 — `ROUND_NOT_STARTED`(라운드 시작 전/카운트다운 중), `DUPLICATE_SEQUENCE`(이미 처리한 clientSequence), `ROUND_ENDED`(라운드 종료 후). 이 셋은 **에러 채널이 아니라 200 성격의 ack로 옵니다** — 요청 자체는 실패가 아니라는 뜻입니다. `ROUND_ENDED`일 때는 `rankings`가 빈 배열로 옵니다.

### 파티 브로드캐스트 (`/topic/parties/{partyId}`)

| `event` | 발생 시점 | `data` 구조 |
|---|---|---|
| `user-entered` | 누군가 입장(본인 포함, 자신의 입장도 이 채널로 수신될 수 있음) | `{ nickname, characterId, characterImageUrl, role }` |
| `message` | 누군가 메시지 전송 | `ChatMessageResponse` |
| `user-left` | 누군가 퇴장 | `{ nickname, role }` |
| `party-phase-changed` | 파티 진행 단계 전환(음악 → 촛불끄기 → 박터뜨리기 등) | `{ partyId, phase, phaseStartedAt, serverNow }` |
| `party-ending` | 60초 종료 카운트다운 시작 | `{ partyId, endingStartedAt, endedAt, endingReason, hostNickname, serverNow }` |
| `party-ended` | 실시간 파티 종료 | `{ partyId, endedAt, hostNickname, serverNow }` |
| `candle-blow-started` / `candle-blow-progress` / `candle-blow-ended` | 촛불끄기 세션 시작/진행/종료 | `CandleBlowResponse` (위 참고) |
| `burst-game-started` | 박터뜨리기 라운드 시작 | `{ partyId, status, startedAt, endsAt, totalTapCount, stateVersion, serverTime }` |
| `burst-game-progress` | 박터뜨리기 탭 집계 갱신(250ms 단위로 묶어서 전송) | `{ partyId, totalTapCount, endsAt, stateVersion, serverTime, rankings }` |
| `burst-game-ended` | 박터뜨리기 라운드 종료 | `{ partyId, status, endsAt, totalTapCount, stateVersion, serverTime, rankings }` |
| `fireworks` | 누군가 폭죽 트리거 | `{ partyId, participantId, nickname }` |

`role`은 `CELEBRANT`(주최자) 또는 `PARTICIPANT`(참가자)입니다. `party-ending`의 `endingReason`은 `HOST_REQUEST | HOST_LEFT | TIME_LIMIT_REACHED` 중 하나입니다.

`burst-game-*`의 `rankings`는 `SubmitBurstGameTapResponse`(위 참고)와 같은 `{ rank, participantId, nickname, characterId, characterThumbnailImageUrl, role, tapCount }` 구조의 배열이며, 진행 중에는 상위 3명만 옵니다. `burst-game-progress`는 짧은 시간에 몰린 탭들을 250ms 단위로 묶어 최신 상태 하나만 보내는 throttle이 걸려 있습니다 — 탭마다 매번 오지 않습니다.

**ChatMessageResponse**
```json
{
  "messageId": 2,
  "content": "반갑습니다!",
  "senderNickname": "곰돌이",
  "senderCharacterId": null,
  "senderCharacterImageUrl": null,
  "senderRole": "CELEBRANT",
  "sentAt": "2026-05-07T10:01:00"
}
```

**party-ending**
```json
{
  "partyId": 1,
  "endingStartedAt": "2026-05-19T20:10:00",
  "endedAt": "2026-05-19T20:11:00",
  "endingReason": "TIME_LIMIT_REACHED",
  "hostNickname": "홍길동",
  "serverNow": "2026-05-19T20:10:00"
}
```

`serverNow`는 이벤트를 보낸 시점의 서버 시각입니다. 남은 시간을 `endedAt - Date.now()`로 계산하면
기기 시계가 서버보다 느린 만큼 카운트다운이 실제보다 크게 표시되어, 화면에 아직 시간이 남은 상태로
`party-ended`가 도착합니다. 수신 즉시 `offset = Date.now() - serverNow`를 구해 두고
`남은 시간 = endedAt - (Date.now() - offset)`으로 계산하세요 (`burst-game-*`의 `serverTime`과 같은 방식).
`party-ended`와 `POST /api/v1/parties/{partyId}/realtime-end` 응답에도 같은 목적의 `serverNow`가 들어 있습니다.

**시각 문자열 형식** — 이 문서의 모든 시각 필드는 타임존 오프셋이 없는 `LocalDateTime` 문자열이며,
`app.time-zone`(기본 `Asia/Seoul`) 기준 벽시계 시각입니다. 소수점 이하 자릿수는 값의 출처에 따라
다릅니다 — `serverNow`는 나노초 9자리, DB에서 읽은 `endedAt`·`endingStartedAt`은 마이크로초 6자리
또는 소수점 없음이 나올 수 있으니 자릿수를 고정으로 가정하지 마세요.

오프셋이 없으므로 파서가 어떤 타임존으로 해석하느냐에 따라 절대 시각이 달라집니다. 다만
`offset`과 `남은 시간`을 위 공식으로 계산하면 **`serverNow`와 `endedAt`을 같은 파서로 파싱하는 한**
해석 오차가 양쪽에서 상쇄되어 결과는 정확합니다. 두 필드를 서로 다른 방식으로 파싱하거나
한쪽만 오프셋을 붙여 해석하면 그 차이가 그대로 오차로 남습니다.

`party-ended`를 받으면 서버는 별도로 접속을 끊지 않습니다 — 클라이언트가 이 이벤트를 신호로 직접 연결을 종료하세요.

### 에러 (`/topic/errors/{clientRequestId}`)

```json
{ "event": "error", "data": { "code": "CHAT_NOT_ACTIVE", "message": "현재 채팅이 활성화된 시간이 아닙니다" } }
```

주요 `code` 값 (SSE와 동일한 에러 코드 체계를 공유합니다):

| code | 상황 |
|---|---|
| `INVITE_LINK_EXPIRED` | 만료된 초대 링크로 입장 |
| `CHAT_NOT_ACTIVE` | 파티가 `LIVE_OPEN`이 아닐 때 입장/전송 |
| `CHAT_NOT_SUPPORTED` | 실시간(채팅) 파티가 아님 |
| `PARTY_HOST_NICKNAME_NOT_EDITABLE` | 주최자가 재입장 시 닉네임을 바꾸려 함 |
| `PARTY_NICKNAME_DUPLICATED` | 닉네임 중복 |
| `PARTY_NOT_FOUND` / `CHARACTER_NOT_FOUND` | 존재하지 않는 리소스 |
| `PARTY_FORBIDDEN` | 다른 파티의 participantToken을 사용 |
| `CANDLE_BLOW_NOT_STARTED` | 촛불끄기 세션이 아직 시작되지 않음 |
| `INVALID_INPUT` | 요청 페이로드 검증 실패 (닉네임 20자 초과, candleId 범위 밖 등) |
| `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

> 박터뜨리기의 `ROUND_NOT_STARTED` / `DUPLICATE_SEQUENCE` / `ROUND_ENDED`는 이 에러 채널로 오지 않습니다 — `tap-submitted` ack의 `ignoredReason` 필드로 옵니다 (위 "SubmitBurstGameTapResponse" 참고).

## 재연결

WebSocket은 브라우저 `EventSource`와 달리 자동 재연결을 지원하지 않는 프로토콜입니다 — "끊기면 다시 붙는" 동작 자체는 클라이언트 라이브러리가 담당해야 하고, 서버 코드로 대신할 수 없습니다. 다만 재연결 이후 상태 복구는 서버가 이미 책임지고 있습니다.

**서버가 이미 하는 것**
- **하트비트(10초 간격)**: 서버·클라이언트가 10초마다 서로 생존을 확인합니다. 끊긴 연결을 TCP 타임아웃(수 분)까지 기다리지 않고 훨씬 빨리 감지할 수 있어, 클라이언트가 재연결을 그만큼 빨리 시작할 수 있습니다.
- **`participantToken` 재입장**: 재연결 후 같은 `participantToken`으로 다시 입장 요청을 보내면(위 "입장 + 구독" 참고) 기존 참가자로 복구되고, `entered` 응답에 그동안의 채팅 내역이 전부 다시 포함됩니다. 메시지 유실 없이 이어볼 수 있습니다.

**클라이언트가 해야 하는 것**: 연결이 끊겼음을 감지하고 다시 `client.activate()`를 호출한 뒤, 보관해 둔 `participantToken`으로 재입장 요청을 보내는 것. `@stomp/stompjs`는 `reconnectDelay` 옵션으로 이 루프를 기본 제공합니다.

```js
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${jwt}` }, // 게스트는 생략
  reconnectDelay: 5000, // 끊기면 5초 후 자동 재연결 시도
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  onConnect: () => {
    // 최초 입장이든 재연결이든 동일한 요청 — participantToken이 있으면 재입장으로 처리됩니다.
    client.publish({
      destination: `/app/party-invites/${inviteToken}/realtime-participants`,
      body: JSON.stringify({ nickname, characterId, participantToken, clientRequestId: crypto.randomUUID() }),
    });
  },
});
client.activate();
```

## 알려진 제약 (SSE 대비)

- **단일 인스턴스 브로드캐스트**: 서버가 여러 대로 확장되면 다른 인스턴스에 붙은 클라이언트끼리는 브로드캐스트가 전달되지 않습니다. 현재는 단일 인스턴스 운영을 전제로 합니다.

## 참고

- 실측 기반 SSE 대비 트레이드오프: `docs/superpowers/specs/2026-08-18-sse-websocket-tradeoffs.md`
