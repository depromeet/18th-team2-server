# 실시간 파티 촛불끄기 설계

- 작성일: 2026-05-24
- 기준 브랜치: `develop`
- 목적: 실시간 파티에서 박터뜨리기 전 9개의 공유 촛불을 함께 끄는 단계의 API/SSE/상태 계약을 정의한다.

---

## 0. 빠른 요약 [기획/API/구현]

핵심 동작:

- 실시간 파티가 시작된 뒤 41초가 지나면 촛불끄기 단계가 서버 기준으로 시작된다.
- 촛불은 파티 전체가 공유하는 9개 고정 슬롯이다.
- 실시간 파티 참여자라면 누구나 촛불을 끌 수 있다.
- 이미 꺼진 촛불을 다시 누르면 실패가 아니라 `200 OK`로 현재 상태를 반환한다.
- 9개 촛불이 모두 꺼지거나 시작 후 45초가 지나면 촛불끄기 단계는 종료된다.
- 촛불끄기 종료는 박터뜨리기 자동 시작이 아니다. 참여자 중 누군가가 다음 버튼으로 기존 박터뜨리기 start API를 호출해야 한다.

기본 정책:

```kotlin
CANDLE_BLOW_START_DELAY_SECONDS = 41
CANDLE_BLOW_DURATION_SECONDS = 45
CANDLE_COUNT = 9
```

종료 사유:

| 값 | 설명 |
|---|---|
| `ALL_EXTINGUISHED` | 9개 촛불이 모두 꺼져 즉시 종료 |
| `TIMEOUT` | 시작 후 45초가 지나 자동 종료 |

용어:

- `finished`: 촛불끄기 단계가 더 이상 입력을 받지 않는 상태다. `ALL_EXTINGUISHED`, `TIMEOUT` 모두 포함한다.
- `completed`: 모든 촛불이 꺼진 성공 케이스만 의미하므로 박터뜨리기 선행 조건에는 사용하지 않는다.

---

## 1. 사용자 흐름 [기획/API]

```text
참여자/호스트                         서버
  │                                    │
  │ 실시간 파티 입장 + SSE 연결         │
  │                                    │
  │ party.startedAt + 41초              │
  │◄── event: candle-blow-started ─────│
  │                                    │
  │── POST /parties/{id}/              │
  │   candle-blow/candles/{candleId} ─►│  촛불 끄기
  │◄── 200 OK 현재 촛불 상태 ─────────│
  │◄── event: candle-blow-progress ───│
  │                                    │
  │ 9개 모두 꺼짐 또는 45초 경과        │
  │◄── event: candle-blow-ended ──────│
  │                                    │
  │ 참여자 중 누군가 다음 버튼 클릭      │
  │── POST /parties/{id}/              │
  │   burst-game/start ───────────────►│
  │◄── event: burst-game-started ─────│
```

촛불끄기는 별도 SSE 연결을 만들지 않는다. 실시간 파티 입장 시 유지 중인 기존 파티 SSE 채널로 시작/진행/종료 이벤트를 보낸다.

---

## 2. 백엔드 책임 [구현]

| 영역 | 설명 |
|---|---|
| 시작 예약 | `party.startedAt + 41초`에 촛불끄기 시작 예약 및 SSE 발송 |
| 복구 | 앱 재시작, SSE 유실, 늦은 입장 시 상태 조회 또는 입장 이벤트로 현재 단계 복구 |
| 참여자 검증 | JWT 또는 `X-Participant-Token`으로 실시간 파티 참여자 확인 |
| 공유 상태 관리 | 파티별 9개 촛불의 extinguished 상태를 단일 aggregate로 관리 |
| 멱등 처리 | 이미 꺼진 촛불 클릭은 `200 OK`로 현재 촛불 상태를 그대로 반환 |
| 종료 처리 | 9개 모두 꺼짐 또는 45초 타임아웃 시 finished 상태 전이 |
| 박터뜨리기 연동 | `CandleBlowStatusReader` 계열 계약은 `finished` 여부를 반환하도록 정렬 |

---

## 3. API 계약 [API]

### 3-1. 촛불끄기 상태 조회

```http
GET /api/v1/parties/{partyId}/candle-blow
Authorization: Bearer {token}
X-Participant-Token: {participantToken}
```

용도:

- SSE 유실 후 상태 복구
- 늦게 입장한 참여자의 현재 단계 복구
- 박터뜨리기 start 버튼 노출 여부 판단

응답:

```json
{
  "partyId": 1,
  "status": "ACTIVE",
  "candles": [
    { "candleId": 1, "extinguished": true },
    { "candleId": 2, "extinguished": false },
    { "candleId": 3, "extinguished": true },
    { "candleId": 4, "extinguished": false },
    { "candleId": 5, "extinguished": true },
    { "candleId": 6, "extinguished": false },
    { "candleId": 7, "extinguished": false },
    { "candleId": 8, "extinguished": false },
    { "candleId": 9, "extinguished": false }
  ],
  "finishedReason": null
}
```

상태:

| 값 | 설명 |
|---|---|
| `WAITING` | `party.startedAt + 41초` 전 |
| `ACTIVE` | 촛불끄기 입력 가능 |
| `FINISHED` | `ALL_EXTINGUISHED` 또는 `TIMEOUT`으로 종료 |

처리 정책:

- 파티가 없으면 `PARTY_NOT_FOUND`.
- `partyOption != REALTIME`이면 `PARTY_NOT_REALTIME`.
- 실시간 파티 참여자가 아니면 `UNAUTHORIZED` 또는 `PARTY_FORBIDDEN`.
- 아직 시작 전이면 `WAITING` 상태를 반환한다.
- 시작/종료 예약 이벤트가 유실됐더라도 조회 시점의 서버 시간으로 lazy transition을 수행할 수 있다.
- 조회 lazy transition은 party 단위 lock 또는 compare-and-set 안에서 수행한다.
  - `status == WAITING && now >= party.startedAt + 41초`이면 `transitionToActiveOnce()`와 `emitStartedEventOnce()`를 같은 critical section에서 예약한다.
  - `status == ACTIVE && now >= endsAt`이면 `transitionToFinishedOnce()`와 `emitEndedEventOnce()`를 같은 critical section에서 예약한다.
  - persisted store로 교체할 때는 동등한 deduplication marker로 `candle-blow-started`, `candle-blow-ended`가 각각 정확히 1회만 발행되도록 보장한다.

### 3-2. 촛불 끄기

```http
POST /api/v1/parties/{partyId}/candle-blow/candles/{candleId}
Authorization: Bearer {token}
X-Participant-Token: {participantToken}
```

요청 body 없음.

응답:

```json
{
  "partyId": 1,
  "status": "ACTIVE",
  "candles": [
    { "candleId": 1, "extinguished": true },
    { "candleId": 2, "extinguished": false },
    { "candleId": 3, "extinguished": true },
    { "candleId": 4, "extinguished": true },
    { "candleId": 5, "extinguished": true },
    { "candleId": 6, "extinguished": false },
    { "candleId": 7, "extinguished": false },
    { "candleId": 8, "extinguished": false },
    { "candleId": 9, "extinguished": false }
  ],
  "finishedReason": null
}
```

멱등 응답:

```json
{
  "partyId": 1,
  "status": "ACTIVE",
  "candles": [
    { "candleId": 1, "extinguished": true },
    { "candleId": 2, "extinguished": false },
    { "candleId": 3, "extinguished": true },
    { "candleId": 4, "extinguished": true },
    { "candleId": 5, "extinguished": true },
    { "candleId": 6, "extinguished": false },
    { "candleId": 7, "extinguished": false },
    { "candleId": 8, "extinguished": false },
    { "candleId": 9, "extinguished": false }
  ],
  "finishedReason": null
}
```

처리 정책:

- `candleId`는 `1..9`만 허용한다. 범위를 벗어나면 `INVALID_INPUT`.
- `WAITING` 상태이고 `now < party.startedAt + 41초`이면 `CANDLE_BLOW_NOT_STARTED`.
- `WAITING` 상태지만 `now >= party.startedAt + 41초`이면 party 단위 lock 또는 compare-and-set 안에서 lazy `transitionToActiveOnce()`와 `emitStartedEventOnce()`를 예약한 뒤 촛불 끄기 요청을 처리한다.
- `FINISHED` 상태에서 호출하면 `200 OK`와 현재 종료 상태를 반환한다.
- 이미 꺼진 촛불이면 `200 OK`와 현재 상태를 반환한다.
- 새 촛불이 꺼지면 현재 9개 촛불 상태를 반환하고 progress SSE 발송 대상이 된다.
- 새 입력으로 9개가 모두 꺼지면 같은 처리 안에서 `FINISHED`로 전이하고 `finishedReason=ALL_EXTINGUISHED`가 된다.
- 요청 시점에 `now >= endsAt`이면 먼저 `FINISHED/TIMEOUT`으로 전이한 뒤 현재 종료 상태를 반환한다.
- 전체 소등 또는 timeout 전이는 party 단위 lock 또는 compare-and-set 안에서 `status == ACTIVE`를 확인한 경로만 `transitionToFinishedOnce()`와 `emitEndedEventOnce()`를 같은 critical section에서 예약한다.

---

## 4. SSE 이벤트 [API]

### 4-1. `candle-blow-started`

```text
event: candle-blow-started
data: {"partyId":1,"status":"ACTIVE","candles":[{"candleId":1,"extinguished":false},{"candleId":2,"extinguished":false},{"candleId":3,"extinguished":false},{"candleId":4,"extinguished":false},{"candleId":5,"extinguished":false},{"candleId":6,"extinguished":false},{"candleId":7,"extinguished":false},{"candleId":8,"extinguished":false},{"candleId":9,"extinguished":false}],"finishedReason":null}
```

발생 조건:

- `party.startedAt + 41초` 도달
- 서버 재시작 후 복구 시 이미 시작 시간이 지났지만 아직 종료 시간이 지나지 않은 경우
- 상태 조회 또는 촛불 끄기 요청에서 `WAITING` lazy transition이 발생한 경우

정확히 1회 발송 조건:

- scheduler 시작, 서버 재시작 복구, 상태 조회 lazy transition, 촛불 끄기 요청이 동시에 시작을 시도해도 party 단위 lock 또는 compare-and-set에서 `status == WAITING && now >= party.startedAt + 41초`를 획득한 하나의 경로만 `transitionToActiveOnce()`를 수행한다.
- 같은 critical section 안에서 started snapshot과 `emitStartedEventOnce()` 예약 여부를 결정한다.
- persisted store 구현으로 교체할 경우에는 status 전이와 event deduplication marker 저장을 하나의 원자적 연산으로 처리한다.

### 4-2. `candle-blow-progress`

```text
event: candle-blow-progress
data: {"partyId":1,"status":"ACTIVE","candles":[{"candleId":1,"extinguished":true},{"candleId":2,"extinguished":false},{"candleId":3,"extinguished":true},{"candleId":4,"extinguished":true},{"candleId":5,"extinguished":true},{"candleId":6,"extinguished":false},{"candleId":7,"extinguished":false},{"candleId":8,"extinguished":false},{"candleId":9,"extinguished":false}],"finishedReason":null}
```

발생 조건:

- 새 촛불이 실제로 꺼진 경우만 발송한다.
- 이미 꺼진 촛불 클릭에는 SSE를 재발송하지 않는다.

### 4-3. `candle-blow-ended`

```text
event: candle-blow-ended
data: {"partyId":1,"status":"FINISHED","candles":[{"candleId":1,"extinguished":true},{"candleId":2,"extinguished":true},{"candleId":3,"extinguished":true},{"candleId":4,"extinguished":true},{"candleId":5,"extinguished":true},{"candleId":6,"extinguished":true},{"candleId":7,"extinguished":true},{"candleId":8,"extinguished":true},{"candleId":9,"extinguished":true}],"finishedReason":"ALL_EXTINGUISHED"}
```

발생 조건:

- 9개 촛불이 모두 꺼짐
- 시작 후 45초 경과

정확히 1회 발송 조건:

- scheduler 종료, 상태 조회 lazy transition, 촛불 끄기 요청이 동시에 종료를 시도해도 party 단위 lock 또는 compare-and-set에서 `status == ACTIVE`를 획득한 하나의 경로만 `transitionToFinishedOnce()`를 수행한다.
- 같은 critical section 안에서 ended snapshot과 `emitEndedEventOnce()` 예약 여부를 결정한다.
- persisted store 구현으로 교체할 경우에는 status 전이와 event deduplication marker 저장을 하나의 원자적 연산으로 처리한다.

`candle-blow-ended`는 박터뜨리기를 자동 시작하지 않는다. 참여자 중 누군가가 다음 버튼을 눌러 `POST /api/v1/parties/{partyId}/burst-game/start`를 호출하면, 기존 박터뜨리기 정책대로 가장 먼저 도착한 요청이 라운드를 생성하고 전체 파티 SSE로 `burst-game-started`가 발송된다.

---

## 5. 박터뜨리기 연동 [구현]

기존 박터뜨리기 start 선행 조건은 `촛불끄기 완료(completed)`가 아니라 `촛불끄기 종료(finished)`로 해석한다.

```kotlin
interface CandleBlowStatusReader {
    fun isCandleBlowFinished(partyId: Long): Boolean
}
```

정책:

- `ALL_EXTINGUISHED`, `TIMEOUT` 모두 `true`.
- `WAITING`, `ACTIVE`는 `false`.
- 박터뜨리기 active 라운드가 이미 있으면 기존 정책처럼 촛불 상태를 재검증하지 않는다.
- 촛불끄기 종료 전 박터뜨리기 start 호출은 `BURST_GAME_NOT_READY`.

---

## 6. 상태 저장 전략 [구현]

1차 구현은 DB 저장 없이 in-memory session으로 처리한다.

전제:

- 현재 배포가 active app instance 하나로 실시간 트래픽을 받는 구조다.
- 촛불끄기 상태는 파티 종료 후 장기 보관할 결과가 아니라 박터뜨리기 전 실시간 phase 상태다.
- 서버 재시작 시 진행 중 촛불 상태가 유실될 수 있으므로, 1차 구현은 이 한계를 명확히 알고 사용한다.
- 촛불끄기 세션은 박터뜨리기 시작 전까지 재조회와 선행 조건 확인에 필요하므로 종료 즉시 삭제하지 않는다.
- 인메모리 누수를 막기 위해 `endsAt + 10분`이 지나면 cleanup 대상이 된다.
- 박터뜨리기 시작이 성공한 뒤에는 촛불 상태가 더 이상 필요하지 않으므로 해당 파티 촛불 세션을 제거할 수 있다.

확장성 고려:

- 촛불 상태 접근은 `CandleBlowSessionStore` 포트 뒤에 둔다.
- 나중에 여러 app instance가 동시에 실시간 트래픽을 받거나, 배포 중 진행 상태 보존이 필요해지면 store 구현만 교체할 수 있게 한다.
- 현재 문서는 Redis 전환을 구현 범위로 확정하지 않는다.
- HTTP/SSE 계약은 store 구현 방식과 분리해서 유지한다.

---

## 7. 아키텍처 메모 [구현]

1차 구현은 기존 `burstgame` feature 안의 선행 단계로 둔다.

이유:

- 기존 박터뜨리기 설계와 코드가 이미 `CandleBlowStatusReader` 포트를 가지고 있다.
- 촛불끄기는 현재 독립 화면/아카이브/DB 영속 결과가 아니라 박터뜨리기 직전 실시간 phase다.
- `party` 기능은 실시간 파티 존재/참여자 검증만 제공하고, 촛불 상태 집계는 직접 알지 않는다.
- `CandleBlowSession` aggregate 상태 전이와 store mutation은 `CandleBlowService`가 담당하고, UseCase는 참여자 검증과 응답 변환 흐름을 조합한다.

패키지 방향:

```text
burstgame/api
burstgame/application/usecase
burstgame/application/service
burstgame/application/port
burstgame/domain
burstgame/infrastructure/candle
burstgame/infrastructure/realtime
burstgame/infrastructure/scheduler
```

주의:

- `burstgame`이 `SseEmitterRegistry`에 직접 의존하지 않고 기존 `ChatSseGateway` 기반 브로드캐스터 뒤에서 SSE를 발송한다.
- `burstgame`이 `party.infrastructure.persistence`에 직접 의존하지 않는다.
- ArchUnit feature 목록에 `burstgame`을 포함할지 별도 단계에서 보강한다.

---

## 8. 테스트 포인트

- `party.startedAt + 41초` 전 상태 조회는 `WAITING`.
- `party.startedAt + 41초` 이후 상태 조회는 `ACTIVE`.
- 촛불 1개 끄기 성공 시 해당 `candleId`의 `extinguished`가 `true`로 바뀐다.
- 이미 꺼진 촛불 재클릭은 `200 OK`와 현재 9개 촛불 상태를 반환한다.
- 9개가 모두 꺼지면 `FINISHED`, `finishedReason=ALL_EXTINGUISHED`.
- 45초가 지나면 `FINISHED`, `finishedReason=TIMEOUT`.
- 종료 후 클릭은 `200 OK`와 현재 종료 상태를 반환한다.
- `FINISHED` 상태에서는 박터뜨리기 start가 가능하다.
- `WAITING`/`ACTIVE` 상태에서는 박터뜨리기 start가 `BURST_GAME_NOT_READY`.
