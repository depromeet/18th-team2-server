# 실시간 파티 박터뜨리기 설계

- 작성일: 2026-05-14
- 기준 브랜치: `develop`
- 목적: 실시간 파티에서 채팅/촛불끄기 이후 20초 동안 참여자가 함께 원을 터치하고, 개인 터치 수와 실시간 순위를 집계하는 박터뜨리기 기능 설계
- 구현 반영 상태: 구현 브랜치 기준 API 응답/이벤트 계약과 맞춰 갱신한다.

---

## 0. 빠른 요약 [기획/API/구현]

핵심 동작:

- 실시간 파티 SSE 연결을 유지한 상태에서 20초 동안 터치 수를 집계한다.
- 진행 중에는 순위 entry 기준 상위 3명까지만 `burst-game-progress`로 브로드캐스트한다.
- 진행 중 합산 터치 수는 노출하지 않고, 호출자 개인 터치 수(`myTapCount`)를 submit/state 응답으로 제공한다.
- 종료 시에는 최종 총 터치 수와 전체 참가자 최종 순위를 `burst-game-ended`로 브로드캐스트한다.
- 100회 달성은 종료 조건이 아니며, `colorChanged = true`로만 상태를 내려준다.
- 1차 구현은 DB 저장 없이 in-memory session + 5분 TTL로 처리한다.

블로커 결정 사항:

- [x] 시작 권한: 촛불끄기가 완료된 상태라면 실시간 파티 참여자 누구나 시작 가능
- [x] 재시작 정책: 파티당 1회만 허용
- [x] 동점 처리: 공동 순위 허용. 진행 중은 entry 기준 상위 3명까지만, 종료 결과는 전체 순위 제공

기본값으로 진행 가능한 기술 결정:

- 박터뜨리기 라운드는 파티당 1회 정책이므로 외부 API는 `partyId` 기준으로 식별한다.
- `stateVersion`은 라운드 session lock 안에서 집계 반영과 함께 증가
- progress SSE는 throttle로 중간 버전이 누락될 수 있는 최신 aggregate snapshot
- 종료는 scheduler와 submit/상태 조회 lazy 종료 체크를 모두 둔다.
- 박터뜨리기 start는 촛불끄기 완료 상태를 선행 조건으로 검증한다.
- 참가자별 tap rate limit은 독립된 두 제약으로 검사한다.
  - 초당 제한: 참가자별 token bucket 기준 초당 20회 refill, burst capacity 30회까지 반영
  - 라운드 누적 제한: 참가자별 라운드 누적 400회까지 반영

---

## 1. 기능 요약 [기획]

실시간 파티(`REALTIME`) 진행 중 "촛불끄기" 다음 단계로 20초짜리 박터뜨리기 라운드를 시작한다.

참여자들은 20초 동안 터치 액션을 전송한다. 서버는 모든 참여자의 터치 수를 집계하고, 진행 중에는 전체 참여자에게 순위 entry 기준 상위 3명까지만 실시간으로 브로드캐스트한다. 진행 중 화면에서 필요한 개인 터치 수는 submit/state 응답의 `myTapCount`를 기준으로 보여준다. 20초가 끝나면 최종 총 터치 수와 전체 참가자 최종 순위를 브로드캐스트한다.

핵심 정책:

- 라운드 시간은 서버 기준 20초다.
- 상태 변경 기준 터치 수는 100회다.
- 총 터치 수가 100회 이상이 되면 `colorChanged = true`로 본다. 이 값은 진행 상태 판단을 위한 서버 집계값이다.
- 100회 기준값은 서버 정책 상수로 관리하고, 응답에는 `colorChanged`만 내려준다.
- 100회 달성은 종료 조건이 아니다. 100회 이후에도 20초가 끝날 때까지 계속 터치할 수 있다.
- 진행 중 순위는 rank group 기준이 아니라 정렬된 entry 기준 상위 3명까지만 실시간 갱신한다.
- 진행 중 합산 터치 수(`totalTapCount`)는 표시용 계약에서 제외하고, 개인 터치 수는 `myTapCount`로 제공한다.
- 백엔드가 tap batch를 집계하고 SSE로 `burst-game-progress` 이벤트를 브로드캐스트한다.

---

## 2. 백엔드 책임 [구현]

| 영역 | 설명 |
|---|---|
| 라운드 시작 | 서버 기준 시작/종료 시각 확정 |
| 참여자 검증 | JWT 또는 `X-Participant-Token`으로 실시간 파티 참여자와 프로필 존재 여부 확인 |
| 선행 단계 검증 | 촛불끄기 완료 상태인지 확인 |
| 시간 검증 | 20초 진행 구간 안에서만 tap batch 반영 |
| 터치 수 집계 | 참가자별 터치 수, 전체 합산 터치 수, 진행 중 상위 3명 순위, 종료 시 전체 순위 계산 |
| 중복 batch 방지 | 참가자별 `clientSequence`를 기준으로 동일 batch 재처리 방지 |
| 실시간 브로드캐스트 | 실시간 파티 입장 시 이미 연결된 기존 SSE 스트림으로 진행/종료 이벤트 전송 |
| 결과 유지 | 라운드 종료 후 짧은 시간 동안 결과 재조회가 가능하도록 메모리에 TTL 유지 |

---

## 3. 사용자 흐름 [기획/API]

```text
참여자/호스트                         서버
  │                                    │
  │── POST /party-invites/{token}/     │
  │   realtime-participants/stream ───►│  0. 실시간 파티 입장 + SSE 연결
  │◄── event: entered ────────────────│
  │                                    │
  │ 채팅 진행                          │
  │ 촛불끄기 진행                      │
  │                                    │
  │── POST /parties/{id}/              │
  │   burst-game/start ───────────────►│  1. 실시간 파티/참여자 검증
  │                                    │  2. 라운드 생성 또는 기존 active 라운드 반환
  │◄── { partyId, startedAt, endsAt } ─│
  │◄── event: burst-game-started ─────│  3. 기존 SSE 구독자에게 시작 이벤트
  │                                    │
  │── POST /parties/{id}/burst-game/   │
  │   taps { tapCount, sequence } ────►│  4. tap batch 반영
  │◄── { myTapCount, rankings } ──────│
  │◄── event: burst-game-progress ────│  5. 기존 SSE로 상위 3명 순위 entry 갱신
  │                                    │
  │ 20초 종료                           │
  │◄── event: burst-game-ended ───────│  6. 기존 SSE로 최종 총합 + 전체 순위 브로드캐스트
```

박터뜨리기는 별도 SSE 연결을 새로 만들지 않는다. 사용자는 실시간 파티 입장 시점부터 이미 SSE 연결을 유지하고 있고, 박터뜨리기 start/progress/end 이벤트도 같은 파티 SSE 채널로 흘러간다.

---

## 4. API 계약 [API]

### 4-1. 박터뜨리기 시작

```http
POST /api/v1/parties/{partyId}/burst-game/start
Authorization: Bearer {token}
X-Participant-Token: {participantToken}
```

인증:

- JWT 또는 `X-Participant-Token` 중 하나 필수
- 해당 파티의 `RealtimeParticipantProfile`이 있어야 한다.

권한:

- 현재 결정: **촛불끄기가 완료된 상태라면 실시간 파티 참여자 누구나 시작 가능**
  - 이유: 박터뜨리기는 촛불끄기 다음 단계이고, 시작 권한보다 선행 단계 완료 여부가 핵심 조건이다.
  - 여러 참여자가 동시에 호출해도 서버는 active 라운드 1개만 생성하고 나머지는 기존 active 라운드를 반환한다.

요청 body 없음.

응답:

```json
{
  "partyId": 10,
  "myParticipantId": 37,
  "startedAt": "2026-05-14T20:10:00",
  "endsAt": "2026-05-14T20:10:20",
  "colorChanged": false,
  "stateVersion": 0,
  "serverTime": "2026-05-14T20:10:00"
}
```

처리 정책:

- 파티가 없으면 `PARTY_NOT_FOUND`.
- `partyOption != REALTIME`이면 `CHAT_NOT_SUPPORTED`.
- 실시간 파티 진행 가능 구간이 아니면 `CHAT_NOT_ACTIVE`.
- 참여자 프로필이 없으면 `UNAUTHORIZED`.
- 이미 active 라운드가 있으면 새로 만들지 않고 기존 라운드를 반환한다.
  - active 라운드가 있다는 것은 이미 선행 조건 검증을 통과했다는 뜻이므로 촛불끄기 완료 상태를 다시 검증하지 않는다.
- 이미 ended session이 TTL 안에 있으면 기존 결과를 반환하지 않고 `BURST_GAME_ALREADY_ENDED (409)`로 막는다.
- active/ended session이 없고 새 라운드를 생성해야 할 때 촛불끄기 완료 상태를 검증한다.
  - 촛불끄기가 완료되지 않았으면 `BURST_GAME_NOT_READY`.
  - TODO: `burstgame.application.service.CandleBlowStatusReader`를 먼저 만들고 `fun isCandleBlowCompleted(partyId: Long): Boolean` 계약으로 촛불 완료 상태 조회 로직을 연결한다. 실제 촛불끄기 feature가 머지되면 인터페이스 signature나 반환 타입은 사전 합의 후 조정한다.
  - 촛불끄기 feature가 아직 머지되지 않은 개발/테스트 단계에서는 항상 `true`를 반환하고 warning log를 남기는 `CandleBlowStatusReaderStub`을 임시 adapter로 둔다.
  - stub은 `local`, `dev`, `test` profile 또는 명시적 feature flag에서만 bean으로 등록하고, prod profile에는 등록하지 않는다.
- 종료 결과 조회는 start API가 아니라 상태 및 결과 조회 API를 사용한다.
- 일반 참가자가 진행 중 라운드 상태를 복구해야 하는 경우 start API가 아니라 `GET /api/v1/parties/{partyId}/burst-game`을 사용한다.
- 파티당 1회 정책이므로 외부 응답과 submit 경로에는 별도 `roundId`를 노출하지 않는다.

Start API side effect 경계:

- Start API handler는 호출자가 "게임을 시작하려는 의도"를 가진 요청으로 본다.
- active session이 없으면 새 round를 만들고 `burst-game-started` SSE를 1회 브로드캐스트한다.
- active session이 있으면 기존 active 상태만 반환하고 start SSE를 재발화하지 않는다.
- start 호출 자체는 권한/참여자/파티 상태 검증과 start attempt 로그/메트릭을 남길 수 있다.
- round 생성 성공 시에는 start success 로그/메트릭을 남길 수 있다.
- SSE broadcast logic은 새 round 생성 성공 또는 명시된 progress/end 전이에만 이벤트를 발화한다.

### 4-2. 터치 batch 반영

```http
POST /api/v1/parties/{partyId}/burst-game/taps
Authorization: Bearer {token}
X-Participant-Token: {participantToken}
```

요청:

```json
{
  "tapCount": 7,
  "clientSequence": 12
}
```

필드:

| 필드 | 타입 | 검증 | 설명 |
|---|---|---|---|
| `tapCount` | number | 1~30 | 이번 batch에 포함된 터치 수 |
| `clientSequence` | number | 1 이상, 참가자별 최대 accepted sequence 대비 gap 1000 이하 | 호출자가 참가자별로 증가시키는 batch 멱등성 키 |

응답:

```json
{
  "partyId": 10,
  "myParticipantId": 37,
  "accepted": true,
  "ignoredReason": null,
  "myTapCount": 11,
  "colorChanged": false,
  "stateVersion": 13,
  "serverTime": "2026-05-14T20:10:07.120",
  "rankings": [
    {
      "rank": 1,
      "participantId": 37,
      "nickname": "토끼왕",
      "characterId": 2,
      "characterImageUrl": "https://example.com/rabbit.png",
      "role": "CELEBRANT",
      "tapCount": 11
    },
    {
      "rank": 2,
      "participantId": 38,
      "nickname": "곰돌이",
      "characterId": 1,
      "characterImageUrl": "https://example.com/bear.png",
      "role": "PARTICIPANT",
      "tapCount": 9
    },
    {
      "rank": 3,
      "participantId": 39,
      "nickname": "고양이",
      "characterId": 3,
      "characterImageUrl": "https://example.com/cat.png",
      "role": "PARTICIPANT",
      "tapCount": 8
    }
  ]
}
```

처리 정책:

- 파티에 진행 중이거나 TTL 안에 남은 라운드가 없으면 `BURST_GAME_NOT_FOUND`.
- TTL 안에 ended session이 남아 있는 종료 라운드에 submit하면 `200 OK`로 submit 응답 스키마를 유지한다.
  - 해당 batch는 반영하지 않는다.
  - `accepted = false`, `ignoredReason = "ROUND_ENDED"`와 종료 시점의 aggregate 상태를 반환한다.
  - TTL 만료로 session이 제거됐거나 해당 파티에 session이 없으면 `BURST_GAME_NOT_FOUND`.
- 참가자가 해당 파티의 실시간 프로필을 갖고 있지 않으면 `UNAUTHORIZED`.
- 요청 시점에 `now >= endsAt`이면 lazy 종료를 먼저 수행하고, 해당 batch는 반영하지 않는다.
  - 응답은 submit 응답 스키마를 유지한다.
  - `accepted = false`, `ignoredReason = "ROUND_ENDED"`와 종료 시점의 aggregate 상태를 반환한다.
  - 최종 전체 순위가 필요한 호출자는 상태 및 결과 조회 API 또는 `burst-game-ended` 이벤트를 사용한다.
- `clientSequence`는 참가자별 순서 보장 수단이 아니라 batch 멱등성 키로 사용한다.
- 참가자별로 이미 처리한 `clientSequence` 집합을 유지한다.
- `clientSequence`가 이미 처리된 값이면 중복 요청으로 본다.
  - 응답은 `200 OK`.
  - tap 수는 추가하지 않는다.
  - `accepted = false`, `ignoredReason = "DUPLICATE_SEQUENCE"`와 현재 집계 상태를 반환한다.
- 아직 처리되지 않은 `clientSequence`는 현재 최대 accepted sequence보다 작거나 같아도 반영할 수 있다.
  - 예: 현재 최대 accepted sequence가 10이고 6~9가 늦게 도착했더라도, 아직 처리된 적이 없으면 중복으로 보지 않는다.
  - 중간 sequence 누락은 모바일 네트워크/재시도 상황에서 자연스럽게 발생할 수 있으므로 서버가 막지 않는다.
- 단, `clientSequence > maxAcceptedSequence + MAX_SEQUENCE_GAP`이면 잘못된 요청으로 본다.
  - 1차 기본값: `MAX_SEQUENCE_GAP = 1000`
  - 응답은 `INVALID_INPUT`.
  - 에러 메시지는 `"clientSequence gap too large"`처럼 원인을 알 수 있게 둔다.
- 요청 `tapCount`는 너무 큰 값 전송을 막기 위해 1~30으로 제한한다.
- 요청 `tapCount` 제한과 별도로 참가자별 초당 반영 가능한 tap 수를 제한한다.
  - 1차 기본값: token bucket refill 초당 20회, burst capacity 30회, 참가자별 라운드 누적 400회.
  - 두 제한은 독립적으로 검사한다.
  - 예: 라운드 시작 직후 `tapCount = 30` 단일 batch는 burst capacity 안에 있으므로 허용할 수 있다.
  - 예: 1초 안에 여러 batch로 31회 이상이 들어오면 token bucket 잔여량을 초과한 batch를 거부한다.
  - 예: 매초 15회씩 들어와도 누적 반영 수가 400회를 넘으면 라운드 누적 제한 위반으로 이후 batch를 거부한다.
  - 초당 제한은 token bucket으로 적용하고, 라운드 누적 제한은 accepted tap count 합계로 적용한다.
  - 초과 시 `BURST_GAME_RATE_LIMITED (429)`를 반환하고 해당 batch는 반영하지 않는다.
- 서버는 batch 반영 후 `burst-game-progress` SSE 이벤트를 브로드캐스트한다.

### 4-3. 라운드 상태 및 결과 조회

```http
GET /api/v1/parties/{partyId}/burst-game
Authorization: Bearer {token}
X-Participant-Token: {participantToken}
```

SSE 재연결, `burst-game-started` 이벤트 유실, 종료 직후 재조회에 모두 사용한다. 파티에 연결된 라운드가 진행 중이면 현재 진행 상태를, 종료됐으면 TTL 안의 최종 결과를 반환한다.

경로를 party 중심으로 둔 이유:

- 이 기능은 파티당 active/ended session을 최대 1개만 유지하는 정책이므로 party 기준 조회가 자연스럽다.
- `GET /api/v1/burst-game/rounds/{roundId}`는 1차 범위에서 제공하지 않는다. 상태/결과 복구는 party 기준 API 하나로 통일한다.

응답:

```json
{
  "partyId": 10,
  "myParticipantId": 37,
  "ended": true,
  "myTapCount": 52,
  "startedAt": "2026-05-14T20:10:00",
  "endsAt": "2026-05-14T20:10:20",
  "totalTapCount": 137,
  "colorChanged": true,
  "stateVersion": 58,
  "serverTime": "2026-05-14T20:10:21.000",
  "rankings": [
    {
      "rank": 1,
      "participantId": 37,
      "nickname": "토끼왕",
      "characterId": 2,
      "characterImageUrl": "https://example.com/rabbit.png",
      "role": "CELEBRANT",
      "tapCount": 52
    },
    {
      "rank": 2,
      "participantId": 38,
      "nickname": "곰돌이",
      "characterId": 1,
      "characterImageUrl": "https://example.com/bear.png",
      "role": "PARTICIPANT",
      "tapCount": 44
    },
    {
      "rank": 3,
      "participantId": 39,
      "nickname": "고양이",
      "characterId": 3,
      "characterImageUrl": "https://example.com/cat.png",
      "role": "PARTICIPANT",
      "tapCount": 41
    }
  ]
}
```

처리 정책:

- 파티에 active session 또는 TTL 안의 ended session이 없으면 `BURST_GAME_NOT_FOUND`.
- 호출자가 해당 파티의 실시간 프로필을 갖고 있지 않으면 `UNAUTHORIZED`.
- `ended = false`이면 진행 중 상태이며, `rankings`는 6절 순위 정책에 따라 정렬된 entry 기준 상위 3명까지만 내려준다.
  - 공동 1등이 5명이더라도 `rankings`는 그중 표시 순서상 앞선 3명까지만 포함한다.
  - 현재 참여자 수가 3명보다 적으면 그 수만큼만 내려준다.
- `ended = true`이면 종료 결과이며, 최종 `totalTapCount`와 전체 참가자 순위를 `rankings`에 내려준다.
  - 종료 결과의 `rankings`는 상위 3명 제한을 적용하지 않는다.
  - 최종 1등은 `rankings`에서 `rank = 1`인 entry로 판단한다.
  - 전원 0회로 종료된 경우에는 `rankings = []`로 내려준다.
- 외부 응답에서는 `endedAt`을 내려주지 않는다.
  - 게임 종료 기준은 항상 `endsAt`이다.
  - scheduler 지연이나 lazy 종료로 실제 종료 commit 시각이 늦어져도, 그 시간은 사용자가 플레이한 시간으로 보이지 않도록 서버 내부 로그/메트릭에서만 다룬다.
- 상태 및 결과 조회 API는 read-only 복구 API다.
  - round를 생성하지 않는다.
  - start attempt/start success 로그나 메트릭을 남기지 않는다.
  - `burst-game-started` SSE를 발화하지 않는다.
  - reconnect 참여자의 진행 중 상태 복구와 종료 결과 재조회에만 사용한다.

---

## 5. SSE 이벤트 [API/구현]

실시간 파티 입장 API에서 이미 생성된 SSE 연결을 그대로 사용한다.

- 박터뜨리기 전용 신규 SSE 연결은 만들지 않는다.
- `start` API는 라운드 상태를 만들고 기존 파티 SSE 구독자에게 `burst-game-started`를 브로드캐스트한다.
- `submit taps` API는 집계 상태를 갱신하고 기존 파티 SSE 구독자에게 `burst-game-progress`를 브로드캐스트한다.
- 종료 scheduler는 기존 파티 SSE 구독자에게 `burst-game-ended`를 브로드캐스트한다.
- SSE 연결이 없는 참여자는 상태 및 결과 조회 API로 현재 상태를 복구할 수 있지만, 정상 흐름에서는 채팅 단계부터 유지하던 SSE로 이벤트를 받는다.

### 5-1. `burst-game-started`

라운드가 시작되면 해당 파티의 기존 SSE 구독자에게 전송한다.

```json
{
  "partyId": 10,
  "status": "ACTIVE",
  "startedAt": "2026-05-14T20:10:00",
  "endsAt": "2026-05-14T20:10:20",
  "colorChanged": false,
  "stateVersion": 0,
  "serverTime": "2026-05-14T20:10:00"
}
```

### 5-2. `burst-game-progress`

tap batch 반영 후 해당 파티의 기존 SSE 구독자에게 전송한다.

이 이벤트는 진행 중 순위 표시의 기준이다. `rankings`는 정렬된 entry 기준 상위 3명까지의 순위 entry와 각 순위자의 터치 수다. 진행 중 합산 터치 수는 노출하지 않는다.

```json
{
  "partyId": 10,
  "colorChanged": false,
  "endsAt": "2026-05-14T20:10:20",
  "stateVersion": 13,
  "serverTime": "2026-05-14T20:10:07.120",
  "rankings": [
    {
      "rank": 1,
      "participantId": 37,
      "nickname": "토끼왕",
      "characterId": 2,
      "characterImageUrl": "https://example.com/rabbit.png",
      "role": "CELEBRANT",
      "tapCount": 11
    },
    {
      "rank": 2,
      "participantId": 38,
      "nickname": "곰돌이",
      "characterId": 1,
      "characterImageUrl": "https://example.com/bear.png",
      "role": "PARTICIPANT",
      "tapCount": 9
    },
    {
      "rank": 3,
      "participantId": 39,
      "nickname": "고양이",
      "characterId": 3,
      "characterImageUrl": "https://example.com/cat.png",
      "role": "PARTICIPANT",
      "tapCount": 8
    }
  ]
}
```

브로드캐스트 빈도:

- batch 요청마다 이벤트를 보낼 수는 있지만, 참여자가 많아지면 SSE가 과도하게 발생할 수 있다.
- 서버는 party 단위로 200~300ms throttle을 적용하여 최신 상태만 브로드캐스트한다.
- throttle 구간 안의 중간 상태 이벤트는 누락될 수 있다.
- progress 이벤트는 모든 tap event 로그가 아니라 최신 aggregate snapshot이다.
- progress 이벤트의 `stateVersion`은 연속적이지 않을 수 있다. 예를 들어 수신자가 13 다음에 18을 받아도 정상이며, 마지막으로 처리한 값보다 크면 최신 상태로 받아들이면 된다.
- 이벤트 수신자는 `stateVersion`이 마지막으로 처리한 값보다 작거나 같으면 stale 이벤트로 보고 무시할 수 있다.
- `stateVersion`은 party session별 단조 증가 값이다. tap batch가 실제로 반영될 때 증가하고, 중복 batch 무시는 증가시키지 않는다.
- in-memory 1차 구현에서는 party session 단위 lock 안에서 tap count 반영, ranking 재계산, `stateVersion` 증가, broadcast snapshot 생성을 하나의 원자적 구간으로 묶는다.
- 최종 종료 이벤트는 throttle과 무관하게 반드시 전송한다.
- end 이벤트의 `stateVersion`은 마지막 progress 이벤트의 `stateVersion`보다 클 수 있다. 이벤트 수신자는 end 이벤트를 최종 결과로 채택한다.
- progress 이벤트는 라운드 종료 기준 시각인 `endsAt`을 내려준다.
  - 클라이언트는 `endsAt`과 현재 시각을 기준으로 countdown을 계산한다.
  - throttle 지연이나 네트워크 지연이 있어도 종료 기준은 `endsAt`으로 고정된다.

### 5-3. `burst-game-ended`

20초가 끝나면 해당 파티의 기존 SSE 구독자에게 전송한다. 종료 이벤트는 최종 총 터치 수와 전체 참가자 최종 순위를 함께 알려준다.

```json
{
  "partyId": 10,
  "status": "ENDED",
  "totalTapCount": 137,
  "colorChanged": true,
  "stateVersion": 58,
  "serverTime": "2026-05-14T20:10:20.000",
  "rankings": [
    {
      "rank": 1,
      "participantId": 37,
      "nickname": "토끼왕",
      "characterId": 2,
      "characterImageUrl": "https://example.com/rabbit.png",
      "role": "CELEBRANT",
      "tapCount": 52
    },
    {
      "rank": 1,
      "participantId": 38,
      "nickname": "곰돌이",
      "characterId": 1,
      "characterImageUrl": "https://example.com/bear.png",
      "role": "PARTICIPANT",
      "tapCount": 52
    },
    {
      "rank": 2,
      "participantId": 39,
      "nickname": "고양이",
      "characterId": 3,
      "characterImageUrl": "https://example.com/cat.png",
      "role": "PARTICIPANT",
      "tapCount": 33
    }
  ]
}
```

---

## 6. 순위 정책 [기획/API]

랭킹 계산:

1. `tapCount DESC`
2. 동점이면 같은 `rank`를 부여한다.
3. 다음 순위는 dense ranking으로 계산한다.
   - 예: 1등 2명, 다음 rank group은 2등

batch 단위 전송 구조에서는 "특정 tap 수에 정확히 먼저 도달한 시각"을 알 수 없다. 따라서 숨은 시간 기준으로 우열을 가르지 않고 공동 순위를 허용한다.

진행 중 이벤트는 정렬된 순위 entry 기준 상위 3명까지만 내려준다.

공동 순위가 있더라도 같은 rank에 속한 참가자를 전부 내려주지 않는다. 따라서 `rankings` 배열은 최대 3개 entry다.

예:

- 1등 2명, 다음 참가자 2등이면 `rankings = [rank 1, rank 1, rank 2]`
- 1등 5명이면 `rankings`에는 표시 순서상 앞선 공동 1등 3명만 포함한다.
- 1등 2명, 2등 4명이면 `rankings`에는 rank 1 참가자 2명과 rank 2 참가자 1명만 포함한다.
- 1등 3명, 다음 rank group이 2등이면 `rankings`에는 rank 1 참가자 3명만 포함한다.
- 응답 소비자는 진행 중 `rankings.length <= 3`만 가정한다. 참여자가 3명보다 적거나 아직 유효한 tap count가 없으면 3개보다 적을 수 있다.

공동 순위 내 정렬은 `participant.id ASC`로 고정한다. 공동 순위 자체는 유지하되 표시 순서를 안정적으로 만들기 위한 정렬이다.

최종 결과는 `rankings`로 전체 참가자 순위를 제공한다. 종료 상태/종료 이벤트에서는 진행 중 상위 3명 제한을 적용하지 않는다.

전원 0회로 종료되면 유효한 최종 순위가 없는 것으로 본다.

- `rankings = []`
- `totalTapCount = 0`
- `colorChanged = false`

참가자 노출 필드:

| 필드 | 설명 |
|---|---|
| `participantId` | 랭킹 표시용 공개 식별자. 인증 수단인 `participantToken`은 전체 SSE에 노출하지 않는다 |
| `nickname` | 실시간 프로필 닉네임 |
| `characterId` | 선택 캐릭터 |
| `characterImageUrl` | 캐릭터 이미지 URL |
| `role` | `CELEBRANT` 또는 `PARTICIPANT` |
| `tapCount` | 참가자 누적 터치 수 |

응답 소비자는 start/submit response의 `myParticipantId`와 SSE ranking entry의 `participantId`를 비교해 현재 사용자의 순위 여부를 판단할 수 있다.

`myTapCount` 정책:

- `myTapCount`는 개인화된 값이므로 전체 브로드캐스트 SSE에는 포함하지 않는다.
- `submit taps` 응답과 상태 및 결과 조회 응답에는 호출자 기준 `myTapCount`를 포함한다.
- 진행 중 개인 tap count는 호출자가 보유한 임시 값을 먼저 반영하고, `submit taps` 응답 또는 상태 및 결과 조회 응답의 `myTapCount`로 보정하는 전제를 둔다.
- 본인이 `rankings`에 포함되면 SSE ranking entry의 `tapCount`로도 확인할 수 있지만, 진행 중 상위 3명 밖일 수 있으므로 개인 count의 기준 응답은 `submit taps`/상태 조회다.

최종 전체 순위(`rankings[]`) 필드:

| 필드 | 설명 |
|---|---|
| `rank` | 공동 순위를 허용하는 최종 순위 |
| `participantId` | participant id |
| `nickname` | 닉네임 |
| `characterId` | 선택 캐릭터 |
| `characterImageUrl` | 캐릭터 이미지 URL |
| `role` | 역할 |
| `tapCount` | 누적 터치 수 |

최종 표시 계약에는 별도 `winners` 필드를 두지 않는다. 최종 1등은 `rankings`에서 `rank = 1`인 entry로 판단한다.

---

## 7. 데이터 유지 방식 [구현]

### 7-1. active round 집계

20초 동안의 active 집계는 애플리케이션 메모리에서 처리하는 것을 추천한다.

이유:

- 터치 이벤트는 짧은 시간에 많이 들어온다.
- 매 터치를 DB에 insert/update하면 불필요하게 쓰기 부하가 커진다.
- 현재 기능은 실시간 표시가 중요하고, raw tap event의 영구 보존 필요가 낮다.
- 파티 종료 후 보관함/회고/관리자 기능 등에서 박터뜨리기 결과를 재사용하지 않는다.

따라서 1차 구현에서는 DB 테이블을 만들지 않고, 라운드 session을 메모리에 두는 편이 낫다.

단, 이 방식은 단일 active application 인스턴스를 전제로 한다. 서버가 여러 대에서 동시에 요청을 받는 구조가 되면 Redis 같은 공유 저장소가 필요하다.

현재 배포가 active slot 하나로 트래픽을 받는 구조라면 in-memory session으로 시작할 수 있다. 추후 horizontal scale이 필요해지면 `BurstGameSessionStore` 인터페이스 뒤 구현을 Redis로 교체한다.

### 7-2. 종료 결과 유지

라운드 종료 후 결과는 DB에 저장하지 않고, 메모리에 짧은 TTL로 유지한다.

추천 TTL:

- 1차 기본값: 5분
- 목적: 종료 이벤트를 놓친 호출자의 결과 재조회, 종료 직후 짧은 재조회 대응

TTL 이후:

- 결과 조회 API는 `BURST_GAME_NOT_FOUND`를 반환한다.
- 파티 종료 후 어디에서도 결과를 재사용하지 않는다는 현재 기획과 맞다.

DB 저장이 필요한 조건:

- 보관함/회고 기능에서 결과를 다시 보여줘야 한다.
- 운영자가 결과를 확인해야 한다.
- 이벤트 결과를 통계/분석에 사용해야 한다.
- 서버 재시작 중에도 결과 조회를 보장해야 한다.

위 조건이 생기면 그때 `burst_game_round`, `burst_game_participant_score` 테이블을 추가한다.

### 7-3. 라운드 식별자

파티당 1회 정책이므로 1차 구현은 외부 API에서 별도 라운드 식별자를 사용하지 않는다.

이유:

- start, submit, 상태 조회 모두 `partyId`만으로 현재 session을 찾을 수 있다.
- 클라이언트가 별도 `roundId`를 저장하거나 전달할 필요가 없다.
- in-memory session을 장기 저장하지 않으므로 DB 식별자 설계도 현재 범위에 포함하지 않는다.

### 7-4. 동시성 제어와 `stateVersion`

파티별 session은 동일 파티의 tap 반영을 직렬화해야 한다.

1차 구현 기준:

- start 요청도 `partyId` 단위 lock 또는 `ConcurrentHashMap.compute(partyId) { ... }`로 직렬화한다.
  - 촛불끄기 완료 직후 여러 참여자가 동시에 start를 호출해도 active session은 1개만 생성되어야 한다.
  - 먼저 session을 생성한 요청만 새 session을 만들고, 나머지 요청은 같은 active 상태를 반환한다.
- `BurstGameSessionStore`가 `partyId`별 session을 보관한다.
- session 내부 mutation은 party 단위 lock 안에서 수행한다.
- start는 `partyId` 단위 직렬화 구간 안에서 session 생성을 끝낸다.
- submit/상태 조회/end도 같은 `partyId` 단위 직렬화 구간에서 session mutation을 처리한다.
- 같은 원자적 구간에서 다음 작업을 함께 처리한다.
  - `clientSequence` 검증
  - rate limit 검증
  - 참가자별 tap count 반영
  - `totalTapCount` 반영
  - ranking 재계산
  - `stateVersion` 증가
  - broadcast snapshot 생성
- lock 밖에서는 이미 만들어진 immutable snapshot만 SSE로 전송한다.
- 실제 SSE emit은 lock 밖에서 별도 executor로 비동기 발화한다.
- SSE broadcast 실패는 라운드 상태에 영향을 주지 않는다.
- SSE emit 실패는 재시도하지 않는다.
  - 실패한 SSE emitter는 기존 registry 정책에 따라 closed/removed 상태로 정리한다.
  - 클라이언트는 reconnect 후 상태 및 결과 조회 API를 호출해 최신 aggregate 상태로 복구한다.
  - 즉, SSE broadcast 실패는 round state를 롤백하거나 `stateVersion`을 되돌리지 않는다.

이렇게 해야 동시 submit 요청에서도 `stateVersion`, 개인/전체 tap count, `rankings`가 서로 다른 시점의 값으로 섞이지 않는다.

### 7-5. 종료 신뢰성

종료는 scheduler만 믿지 않는다.

정책:

- 라운드 시작 시 `endsAt` 기준 종료 scheduler를 등록한다.
- `submit taps`와 상태 조회도 매 호출마다 `now >= endsAt`이면 lazy 종료를 시도한다.
- scheduler와 lazy 종료 중 먼저 lock을 획득한 쪽만 `ACTIVE -> ENDED` 전이를 commit한다.
- 이미 `ENDED`인 session에 대해서는 종료 처리를 다시 수행하지 않는다.
- lazy 종료가 submit 요청에서 발생하거나 이미 `ENDED`인 session에 submit하면 해당 tap batch는 반영하지 않고 submit 응답 스키마로 `accepted = false`, `ignoredReason = "ROUND_ENDED"`를 반환한다.
  - 종료 후 전체 순위가 필요한 호출자는 상태 및 결과 조회 API를 조회하거나 `burst-game-ended` 이벤트를 사용한다.

이 정책으로 GC pause, scheduler 지연, 요청 타이밍 차이로 active session이 오래 남는 문제를 줄인다.

---

## 8. 패키지 구조 [구현]

현재 `develop`에 반영된 4-layer 구조를 기준으로 한다.

현재 feature 패키지 구조:

```text
burstgame/
├── api/
│   ├── BurstGameApi.kt
│   ├── BurstGameController.kt
│   └── dto/
│       └── SubmitBurstGameTapRequest.kt
├── application/
│   ├── dto/
│   ├── event/
│   │   └── BurstGameEndedEvent.kt
│   ├── port/
│   │   ├── BurstGameEndScheduler.kt
│   │   ├── BurstGameEventBroadcaster.kt
│   │   ├── BurstGameSessionStore.kt
│   │   └── CandleBlowStatusReader.kt
│   ├── service/
│   │   └── BurstGameSessionService.kt
│   ├── support/
│   │   ├── BurstGameParticipantResolver.kt
│   │   └── BurstGameStartSupport.kt
│   └── usecase/
│       ├── StartBurstGameUseCase.kt
│       ├── SubmitBurstGameTapUseCase.kt
│       └── GetBurstGameSnapshotUseCase.kt
├── domain/
│   ├── policy/
│   │   └── BurstGameRankingPolicy.kt
│   ├── BurstGameRoundStatus.kt
│   ├── BurstGameSession.kt
│   ├── BurstGameSnapshot.kt
│   └── BurstGameRankingEntry.kt
└── infrastructure/
    ├── candle/
    ├── memory/
    ├── party/
    ├── realtime/
    └── scheduler/
```

`party` 하위에 넣지 않고 `burstgame` feature로 분리하는 이유:

- 파티의 한 단계이지만, 자체 라운드/집계/결과 상태를 가진 독립 기능이다.
- chat, party, rollingpaper처럼 외부 API와 도메인 상태가 생긴다.
- 추후 다른 미니게임이 생길 때 party feature가 비대해지는 것을 막을 수 있다.

cross-feature 의존:

- `burstgame.application.usecase`는 `BurstGameParticipantResolver`를 통해 실시간 파티/참여자 프로필 식별을 위임한다.
- `BurstGameParticipantResolver`는 현재 `party.application.usecase`의 실시간 파티/프로필 해석 UseCase를 재사용한다.
- `burstgame`은 `party.infrastructure.persistence`에 직접 의존하지 않는다.
- `BurstGameEventBroadcaster` 구현체는 `chat.application.port.PartySseEventPublisher`를 통해 기존 파티 SSE 채널에 이벤트를 발행한다.
  - 실제 SSE registry 호출은 `chat.infrastructure.sse.ChatSseGateway` 구현체가 담당한다.
  - `burstgame`은 `chat.infrastructure`나 `SseEmitterRegistry`를 직접 참조하지 않는다.

---

## 9. 에러 코드 [API/구현]

신규 ErrorCode 후보:

| ErrorCode | HTTP | 상황 |
|---|---|---|
| `BURST_GAME_NOT_FOUND` | 404 | 파티에 active/TTL 안의 ended session이 없음 |
| `BURST_GAME_ALREADY_ENDED` | 409 | TTL 안에 남아 있는 ended session에 대해 재시작 시도 |
| `BURST_GAME_NOT_READY` | 400 | 촛불끄기가 아직 완료되지 않은 상태에서 start 호출 |
| `BURST_GAME_RATE_LIMITED` | 429 | 참가자별 허용 tap rate 초과 |

기존 ErrorCode 재사용:

| ErrorCode | 상황 |
|---|---|
| `PARTY_NOT_FOUND` | partyId 없음 |
| `CHAT_NOT_SUPPORTED` | `REALTIME` 파티가 아님 |
| `CHAT_NOT_ACTIVE` | 실시간 파티 진행 시간이 아님 |
| `UNAUTHORIZED` | JWT/participantToken 없음 또는 프로필 식별 실패 |
| `INVALID_INPUT` | 요청 `tapCount`, `clientSequence` 검증 실패 |

---

## 10. 구현 흐름 [구현]

### start

1. `partyId`로 실시간 파티 조회
2. 호출자를 `RealtimeParticipantProfile`로 식별
3. `partyId` 단위 직렬화 구간 진입
4. 파티에 active round가 있으면 촛불끄기 검증을 건너뛰고 기존 active 상태 반환
5. TTL 안의 ended round가 있으면 `BURST_GAME_ALREADY_ENDED`
6. active/ended round가 없으면 촛불끄기 완료 상태 확인
7. in-memory session 생성
9. 20초 뒤 종료 scheduler 등록
10. 기존 파티 SSE 구독자에게 `burst-game-started` 브로드캐스트
11. start response 반환

### submit taps

1. `partyId`로 session 조회
2. 호출자를 `RealtimeParticipantProfile`로 식별
3. session lock 안에서 이미 `ENDED`면 해당 batch는 반영하지 않고 `accepted = false`, `ignoredReason = "ROUND_ENDED"` 반환
4. `ACTIVE`이지만 `now >= endsAt`이면 lazy 종료 시도
5. lazy 종료가 발생했으면 해당 batch는 반영하지 않고 `accepted = false`, `ignoredReason = "ROUND_ENDED"` 반환
6. 종료되지 않은 active session이면 처리된 `clientSequence` 집합 기준으로 중복 여부 확인
7. 참가자별 rate limit 확인
8. 요청 `tapCount`를 참가자별 count와 total count에 반영
9. ranking 재계산
10. `stateVersion` 증가
11. immutable aggregate snapshot 생성
12. 최신 aggregate response 반환
13. throttle 정책에 따라 기존 파티 SSE 구독자에게 `burst-game-progress` 브로드캐스트

### state/result lookup

1. `partyId`로 active 또는 TTL 안의 ended session 조회
2. 호출자를 `RealtimeParticipantProfile`로 식별
3. active session이면 session lock 안에서 `now >= endsAt` lazy 종료 시도
4. 현재 상태 또는 종료 결과 반환

### end

1. scheduler 또는 lazy 종료 경로가 `endsAt` 이후 종료 시도
2. session lock 안에서 `ACTIVE -> ENDED` 전이를 원자적으로 commit
3. 최종 total/rankings 계산
4. 최종 `stateVersion` 확정
5. immutable ended snapshot 생성
6. 기존 파티 SSE 구독자에게 `burst-game-ended` 브로드캐스트
7. session을 ended 상태로 바꾸고 짧은 TTL로 유지
8. TTL 만료 후 session 제거

---

## 11. 구현 계획과 테스트 계획 [구현]

상세 구현 순서와 테스트 케이스는 별도 plan 문서에서 관리한다.

- Plan: `docs/superpowers/plans/2026-05-14-realtime-burst-game.md`

---

## 12. 결정 사항 [기획/PM]

### 12-1. 확정된 블로커 결정

1. 박터뜨리기 시작 조건
   - 결정: 촛불끄기가 완료된 상태라면 실시간 파티 참여자 누구나 시작 가능하다.
   - 백엔드 처리: start API에서 촛불끄기 완료 상태를 검증한다.
   - 촛불끄기 기능이 별도 PR이라면 박터뜨리기는 `CandleBlowStatusReader` 같은 조회 계약에 의존하고, 촛불 상태 생성/집계 자체는 촛불 기능 범위로 둔다.

2. 라운드 재시작 가능 여부
   - 결정: 파티당 1회만 가능하다.
   - 백엔드 처리: TTL 안의 ended session이 있으면 `BURST_GAME_ALREADY_ENDED`.
   - 운영 리스크: 호스트가 실수로 일찍 시작해도 재시작할 수 없다. 정책 변경이 필요하면 별도 후속 PR에서 강제 재시작 API를 검토한다.

3. 순위 동점 처리
   - 결정: 공동 순위를 허용한다.
   - 백엔드 처리: 같은 `tapCount`는 같은 rank를 부여한다.
   - 진행 중 처리: 동점자가 많아도 entry 기준 상위 3명까지만 내려준다.
   - 종료 처리: 상위 제한 없이 전체 참가자 순위를 `rankings`로 내려준다.

### 12-2. 확정된 제품 정책

- 100회 달성 시 서버는 `colorChanged = true`를 내려준다.
- 100회 이후에도 20초가 끝날 때까지 계속 터치하고 순위를 집계한다.
- 진행 중에는 정렬된 순위 entry 기준 상위 3명과 각 순위자의 터치 수만 표시한다.
- 공동 순위가 있어도 진행 중에는 최대 3명까지만 표시한다.
- 진행 중 합산 터치 수는 표시하지 않고, 개인 터치 수는 `myTapCount`로 표시한다.
- 종료 이벤트와 ended 상태/결과 조회에는 최종 총 터치 수와 전체 참가자 순위를 포함한다.
- 전원 0회면 `rankings = []`로 내려준다.
- 현재 기획에서는 파티 종료 후 박터뜨리기 결과를 다른 기능에서 재사용하지 않는다.

### 12-3. 기본값으로 진행 가능한 기술 정책

- 최종 결과는 DB 저장 없이 메모리에 5분 TTL로 유지한다.
- active app instance가 하나면 in-memory aggregate로 시작한다.
- 여러 app instance가 동시에 트래픽을 받으면 Redis 기반 aggregate로 전환한다.
- tap rate limit 1차 기본값은 참가자별 token bucket refill 초당 20회, burst capacity 30회, 참가자별 라운드 누적 400회다.
  - 두 제약은 독립적으로 검사한다.
  - 초당 제한은 token bucket, 라운드 누적 제한은 accepted tap count 합계 기준으로 적용한다.
- rate limit 수치는 QA 중 모바일 입력감에 맞춰 조정 가능하다.

---

## 13. 1차 구현 범위 제안 [구현]

1차 PR:

- `burstgame` feature 패키지 추가
- start / submit taps / 상태 및 결과 조회 API 추가
- 실시간 파티 입장 시 유지 중인 기존 SSE stream에 `burst-game-started`, `burst-game-progress`, `burst-game-ended` 이벤트 추가
- active aggregate는 in-memory session으로 구현
- 종료 결과는 DB 저장 없이 in-memory ended session에 5분 TTL로 유지
- 진행 중 ranking은 entry 기준 상위 3명과 각 순위자의 터치 수만 반환
- 종료 이벤트/상태 결과 조회는 최종 총 터치 수와 전체 참가자 순위를 반환
- SSE progress/end 이벤트에 `stateVersion`, `serverTime` 포함
- 참가자별 최소 rate limit 적용

2차 PR 후보:

- Redis session store 전환
- anti-cheat 고도화
- 보관함/아카이브에서 박터뜨리기 결과 노출이 필요해질 때 DB 저장 추가
- 미니게임 공통 stage 모델 도입
