# Realtime Burst Game Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티에서 촛불끄기 완료 후 20초 동안 참여자 터치 수를 집계하고, 기존 파티 SSE 연결로 시작/진행/종료 이벤트를 브로드캐스트하는 박터뜨리기 기능을 추가한다.

**Architecture:** 현재 `develop`에 반영된 4-layer 구조를 기준으로 `burstgame/api -> application/usecase -> domain -> infrastructure` 흐름을 따른다. feature 경계는 `burstgame`으로 분리하고, 기존 `party` 기능과는 application 경계로 연동한다. SSE 발화는 `chat.application.port.PartySseEventPublisher`를 통해 기존 파티 SSE 채널을 재사용해 `burstgame -> chat.infrastructure` 직접 의존을 피한다.

**Spec Reference:** `docs/superpowers/specs/2026-05-14-realtime-burst-game-design.md`

---

## Decisions

- 박터뜨리기는 `REALTIME` 파티에서만 동작한다.
- 시작 조건은 촛불끄기 완료 상태다.
- 촛불끄기가 완료됐다면 실시간 파티 참여자 누구나 start API를 호출할 수 있다.
- 파티당 라운드는 1회만 허용한다. TTL 안의 ended session이 있으면 재시작하지 않는다.
- 라운드는 서버 기준 20초 동안 진행한다.
- 총 터치 수가 100회 이상이면 `colorChanged = true`가 되지만, 라운드는 계속 진행된다.
- 진행 중에는 합산 터치 수를 표시하지 않고, entry 기준 상위 3명의 ranking entry만 SSE로 브로드캐스트한다.
- 진행 중 개인 터치 수는 `submit taps` 응답과 상태 조회 응답의 `myTapCount`로 제공한다.
- 동점은 공동 순위로 처리한다. 진행 중 `rankings`는 공동 순위 규모와 무관하게 최대 3명만 포함하고, 최종 결과는 전체 참가자 순위를 포함한다.
- 전원 0회로 종료되면 `rankings = []`로 응답한다.
- 외부 응답에는 `endedAt`을 내려주지 않는다. 종료 기준은 항상 `endsAt`이다.
- active 집계와 종료 결과는 1차 구현에서 DB 저장 없이 in-memory session으로 유지한다.
- ended session TTL은 5분으로 둔다.
- 여러 app instance에서 동시에 트래픽을 받는 구조가 되면 Redis session store로 전환한다.

---

## API Contract

### Start

`POST /api/v1/parties/{partyId}/burst-game/start`

- 인증: Bearer token 또는 `X-Participant-Token`
- 성공: active session 상태 반환
- 이미 active session이 있으면 촛불끄기 상태를 재검증하지 않고 기존 active 상태 반환
- TTL 안의 ended session이 있으면 `BURST_GAME_ALREADY_ENDED`
- 새 session 생성 시에만 `CandleBlowStatusReader`(가칭)로 촛불끄기 완료 상태 확인

### Submit Taps

`POST /api/v1/parties/{partyId}/burst-game/taps`

Request:

```json
{
  "tapCount": 7,
  "clientSequence": 12
}
```

- `tapCount`는 1~30
- `clientSequence`는 참가자별 batch 멱등성 키
- 중복 sequence는 `200 OK`, `accepted = false`, `ignoredReason = "DUPLICATE_SEQUENCE"`
- `clientSequence > maxAcceptedSequence + MAX_SEQUENCE_GAP`이면 `INVALID_INPUT`
- `now >= endsAt`이거나 TTL 안의 ended session에 submit하면 batch는 반영하지 않고 `200 OK`, `accepted = false`, `ignoredReason = "ROUND_ENDED"`
- TTL 만료 또는 해당 파티에 session이 없으면 `BURST_GAME_NOT_FOUND`

### State / Result Lookup

`GET /api/v1/parties/{partyId}/burst-game`

- SSE 재연결, start 이벤트 유실, 종료 직후 결과 조회에 사용한다.
- party 기준 API 하나만 제공한다.
- active session이면 현재 aggregate 상태 반환
- ended session이면 TTL 안의 최종 결과 반환
- active 상태에서는 `ended = false`, entry 기준 상위 3명의 `rankings`, 호출자 기준 `myTapCount`를 반환한다.
- ended 결과에서는 `ended = true`, 최종 `totalTapCount`, 전체 참가자 최종 `rankings`, 호출자 기준 `myTapCount`를 반환한다.

---

## File Structure

### 신규 생성

| 경로 | 책임 |
|---|---|
| `src/main/kotlin/com/team2/server/burstgame/api/BurstGameApi.kt` | 박터뜨리기 Swagger 계약 |
| `src/main/kotlin/com/team2/server/burstgame/api/BurstGameController.kt` | start/submit/state-result HTTP API |
| `src/main/kotlin/com/team2/server/burstgame/api/dto/SubmitBurstGameTapRequest.kt` | tap batch 요청 |
| `src/main/kotlin/com/team2/server/burstgame/application/dto/StartBurstGameResponse.kt` | start 응답 |
| `src/main/kotlin/com/team2/server/burstgame/application/dto/SubmitBurstGameTapResponse.kt` | tap batch 응답 |
| `src/main/kotlin/com/team2/server/burstgame/application/dto/BurstGameStateResponse.kt` | 상태/결과 조회 응답 |
| `src/main/kotlin/com/team2/server/burstgame/application/dto/BurstGameRankingResponse.kt` | 순위 entry 응답 |
| `src/main/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCase.kt` | 라운드 시작 |
| `src/main/kotlin/com/team2/server/burstgame/application/usecase/SubmitBurstGameTapUseCase.kt` | tap batch 반영 |
| `src/main/kotlin/com/team2/server/burstgame/application/usecase/GetBurstGameSnapshotUseCase.kt` | 상태/결과 조회 |
| `src/main/kotlin/com/team2/server/burstgame/application/service/BurstGameSessionService.kt` | session 생성/조회/종료 orchestration |
| `src/main/kotlin/com/team2/server/burstgame/application/support/BurstGameParticipantResolver.kt` | 실시간 파티 참여자 식별 |
| `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowStatusReader.kt` | 촛불끄기 완료 상태 조회 포트 |
| `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameEventBroadcaster.kt` | 박터뜨리기 SSE 이벤트 발화 포트 |
| `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameEndScheduler.kt` | 라운드 종료 예약 포트 |
| `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameSessionStore.kt` | session 저장소 포트 |
| `src/main/kotlin/com/team2/server/burstgame/infrastructure/candle/CandleBlowStatusReaderStub.kt` | 촛불끄기 feature 머지 전 임시 stub |
| `src/main/kotlin/com/team2/server/burstgame/domain/BurstGamePolicy.kt` | duration, color threshold, rate limit, TTL 상수 |
| `src/main/kotlin/com/team2/server/burstgame/domain/policy/BurstGameRankingPolicy.kt` | 진행 중 상위 3명 ranking과 종료 전체 ranking 계산 |
| `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameSession.kt` | in-memory aggregate session |
| `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameRoundStatus.kt` | `ACTIVE`, `ENDED` |
| `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameRankingEntry.kt` | ranking entry |
| `src/main/kotlin/com/team2/server/burstgame/infrastructure/memory/InMemoryBurstGameSessionStore.kt` | in-memory session store |
| `src/main/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledBurstGameEndScheduler.kt` | endsAt 기반 종료 scheduler 구현체 |
| `src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcaster.kt` | 기존 SSE registry 기반 이벤트 발화 구현체 |
| `src/main/kotlin/com/team2/server/chat/application/port/PartySseEventPublisher.kt` | 파티 SSE 이벤트 발행 포트 |
| `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSseGateway.kt` | `PartySseEventPublisher` 구현체 |
| `src/test/kotlin/com/team2/server/burstgame/domain/BurstGameSessionTest.kt` | session 도메인 모델 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCaseTest.kt` | start UseCase 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/application/usecase/SubmitBurstGameTapUseCaseTest.kt` | submit UseCase 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/application/usecase/GetBurstGameSnapshotUseCaseTest.kt` | 상태/결과 조회 UseCase 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/domain/policy/BurstGameRankingPolicyTest.kt` | 진행 중/종료 ranking 정책 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/infrastructure/memory/InMemoryBurstGameSessionStoreTest.kt` | 동시성/TTL 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledBurstGameEndSchedulerTest.kt` | 종료 scheduler 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcasterTest.kt` | SSE 이벤트 발화/throttle 테스트 |
| `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt` | API 통합 테스트 |

### 수정

| 경로 | 변경 내용 |
|---|---|
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | `BURST_GAME_*` 에러 코드 추가 |
| `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSseGateway.kt` | 기존 파티 SSE 채널에 박터뜨리기 이벤트를 발행하는 gateway 재사용, 필요 시 최소 확장 |
| `src/main/kotlin/com/team2/server/chat/infrastructure/sse/SseEmitterRegistry.kt` | `ChatSseGateway`만으로 부족한 registry 기능이 있을 때만 최소 수정 |
| `src/main/kotlin/com/team2/server/party/application/service/PartyService.kt`, `RealtimeParticipantProfileService.kt` 또는 신규 reader | 실시간 파티/프로필 조회 계약 재사용 |
| 촛불끄기 feature 병합 후 adapter | `CandleBlowStatusReader` 구현체 추가 |

---

## Task Order

## Task 1: 정책 상수와 도메인 모델 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/BurstGamePolicy.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameRoundStatus.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameRankingEntry.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/BurstGameSession.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/domain/BurstGameSessionTest.kt`

- [ ] `ROUND_DURATION_SECONDS = 20`을 정의한다.
- [ ] `COLOR_CHANGE_TAP_COUNT = 100`을 정의한다.
- [ ] `ENDED_SESSION_TTL = 5 minutes`를 정의한다.
- [ ] 참가자별 rate limit 기본값을 token bucket refill 초당 20회, burst capacity 30회, 참가자별 라운드 누적 400회로 정의한다.
- [ ] `MAX_SEQUENCE_GAP = 1000`을 정의한다.
- [ ] `BurstGameSession`에 `partyId`, `startedAt`, `endsAt`, `status`, `stateVersion`, 참가자별 score map을 둔다.
- [ ] 외부 응답에는 `endedAt`을 포함하지 않는 것을 DTO 설계 기준으로 둔다.

Run:

```bash
./gradlew compileKotlin compileTestKotlin
./gradlew test --tests com.team2.server.burstgame.domain.BurstGameSessionTest
```

## Task 2: ranking 도메인 정책 구현

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/policy/BurstGameRankingPolicy.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/domain/policy/BurstGameRankingPolicyTest.kt`

- [ ] `tapCount DESC`로 정렬한다.
- [ ] 같은 `tapCount`는 같은 rank를 부여한다.
- [ ] 다음 rank는 dense ranking으로 계산한다.
- [ ] 공동 rank 내 표시 순서는 `participant.id ASC`로 고정한다.
- [ ] 진행 중/summary `rankings`는 정렬된 entry 기준 상위 3명까지만 반환한다.
- [ ] 공동 rank에 속한 참가자가 3명을 초과해도 진행 중 `rankings`는 표시 순서상 앞선 3명만 포함한다.
- [ ] 공동 1등이 5명이면 진행 중 `rankings`에는 공동 1등 3명만 포함한다.
- [ ] 종료 결과 `rankings`는 상위 3명 제한 없이 전체 참가자를 반환한다.
- [ ] 전원 0회면 `rankings = []`를 반환한다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.domain.policy.BurstGameRankingPolicyTest
```

## Task 3: in-memory session store와 동시성 제어

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/memory/InMemoryBurstGameSessionStore.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/service/BurstGameSessionService.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/memory/InMemoryBurstGameSessionStoreTest.kt`

- [ ] `partyId` 기준 active/ended session 조회를 지원한다.
- [ ] start는 `partyId` 단위 직렬화 구간에서 active 중복 생성을 막는다.
- [ ] start는 session 생성을 같은 직렬화 구간에서 끝낸다.
- [ ] submit/상태 조회/end는 `partyId` 단위 직렬화 구간에서 session mutation을 처리한다.
- [ ] accepted batch 반영, ranking 재계산, `stateVersion` 증가, immutable broadcast snapshot 생성을 같은 원자 구간에서 처리한다.
- [ ] ranking 재계산은 다른 Service 호출이 아니라 `BurstGameRankingPolicy` 도메인 정책 호출로 처리한다.
- [ ] 중복 batch는 count와 `stateVersion`을 증가시키지 않는다.
- [ ] ended session은 TTL 이후 제거한다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.infrastructure.memory.InMemoryBurstGameSessionStoreTest
```

## Task 4: 외부 의존 인터페이스와 reader 계약 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/support/BurstGameParticipantResolver.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowStatusReader.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameEventBroadcaster.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameEndScheduler.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/BurstGameSessionStore.kt`
- Reuse: party 실시간 파티/프로필 해석 UseCase

- [ ] `BurstGameParticipantResolver`는 Bearer token 또는 `X-Participant-Token`으로 `RealtimeParticipantProfile`을 식별한다.
- [ ] `partyOption != REALTIME`이면 `CHAT_NOT_SUPPORTED`.
- [ ] 실시간 파티 진행 가능 구간이 아니면 `CHAT_NOT_ACTIVE`.
- [ ] 프로필이 없으면 `UNAUTHORIZED`.
- [ ] `CandleBlowStatusReader`는 촛불 완료 여부만 반환하는 application port 인터페이스다.
- [ ] `CandleBlowStatusReader`는 `fun isCandleBlowCompleted(partyId: Long): Boolean` 시그니처를 기본 계약으로 둔다.
- [ ] 새 라운드 생성 시 `CandleBlowStatusReader`로 촛불 완료 상태를 확인한다.
- [ ] 촛불 완료 전이면 `BURST_GAME_NOT_READY`.
- [ ] active 라운드가 이미 있으면 촛불 상태를 재검증하지 않는다.
- [ ] `BurstGameEventBroadcaster`는 start/progress/end 이벤트 발화를 추상화한다.
- [ ] `BurstGameEndScheduler`는 `endsAt` 기준 종료 예약을 추상화한다.
- [ ] UseCase는 broadcaster/scheduler 구현체가 아니라 application port 인터페이스에만 의존한다.
- [ ] 촛불끄기 feature가 아직 머지되지 않았다면 항상 `true`를 반환하고 warning log를 남기는 `CandleBlowStatusReaderStub`을 추가한다.
- [ ] `CandleBlowStatusReaderStub`은 `@Profile("local", "dev", "test")` 또는 명시적 feature flag로 prod 등록을 막는다.
- [ ] 실제 촛불끄기 feature가 머지되면 stub을 실제 adapter로 교체한다.

Run:

```bash
./gradlew compileKotlin compileTestKotlin
```

## Task 5: start UseCase와 API 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/api/BurstGameApi.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/api/BurstGameController.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/dto/StartBurstGameResponse.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/application/usecase/StartBurstGameUseCaseTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`

- [ ] `POST /api/v1/parties/{partyId}/burst-game/start`를 추가한다.
- [ ] request body 없이 처리한다.
- [ ] active session이 있으면 기존 active 상태를 반환한다.
- [ ] TTL 안의 ended session이 있으면 `BURST_GAME_ALREADY_ENDED`.
- [ ] active/ended session이 없을 때만 촛불 완료 상태를 검증한다.
- [ ] `startedAt`, `endsAt`, `stateVersion = 0`, `colorChanged = false`로 session을 생성한다.
- [ ] `BurstGameEndScheduler` 인터페이스로 종료 scheduler를 등록한다.
- [ ] `BurstGameEventBroadcaster` 인터페이스로 기존 파티 SSE 구독자에게 `burst-game-started`를 브로드캐스트한다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.application.usecase.StartBurstGameUseCaseTest
./gradlew test --tests com.team2.server.burstgame.api.BurstGameControllerTest
```

## Task 6: scheduler 구현과 lazy 종료

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledBurstGameEndScheduler.kt`
- Modify: `src/main/kotlin/com/team2/server/burstgame/application/service/BurstGameSessionService.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledBurstGameEndSchedulerTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/memory/InMemoryBurstGameSessionStoreTest.kt`

- [ ] start 시 `BurstGameEndScheduler` 인터페이스를 통해 `endsAt` 기준 종료 작업을 등록한다.
- [ ] `ScheduledExecutorService` 기반 구현체로 종료 작업을 예약한다.
- [ ] submit/상태 조회는 `now >= endsAt`이면 lazy 종료를 시도한다.
- [ ] scheduler와 lazy 종료 중 먼저 lock을 획득한 경로만 `ACTIVE -> ENDED`를 commit한다.
- [ ] 종료 commit 시 최종 total/rankings와 최종 `stateVersion`을 확정한다.
- [ ] ended session은 TTL 동안 유지한다.
- [ ] TTL 만료는 Caffeine `expireAfterWrite(5, MINUTES)` 또는 동등한 lazy 만료 메커니즘으로 처리한다.
- [ ] TTL 만료 후 partyId 조회에서 제거한다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.infrastructure.scheduler.ScheduledBurstGameEndSchedulerTest
./gradlew test --tests com.team2.server.burstgame.infrastructure.memory.InMemoryBurstGameSessionStoreTest
```

## Task 7: submit UseCase와 tap 집계 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/usecase/SubmitBurstGameTapUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/api/dto/SubmitBurstGameTapRequest.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/dto/SubmitBurstGameTapResponse.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/application/usecase/SubmitBurstGameTapUseCaseTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`

- [ ] `POST /api/v1/parties/{partyId}/burst-game/taps`를 추가한다.
- [ ] `tapCount`는 1~30으로 검증한다.
- [ ] `clientSequence`는 1 이상으로 검증한다.
- [ ] 참가자별 처리 완료 `clientSequence` 집합을 기준으로 이미 처리한 sequence만 중복으로 본다.
- [ ] 중복 sequence면 `accepted = false`, `ignoredReason = "DUPLICATE_SEQUENCE"`를 반환한다.
- [ ] `clientSequence > maxAcceptedSequence + MAX_SEQUENCE_GAP`이면 `INVALID_INPUT`으로 거부하고 `"clientSequence gap too large"` 메시지를 내려준다.
- [ ] `MAX_SEQUENCE_GAP` 이하의 sequence gap은 허용하고, 늦게 도착한 미처리 sequence도 반영한다.
- [ ] 참가자별 초당/라운드 누적 rate limit을 적용한다.
- [ ] rate limit 초과 시 `BURST_GAME_RATE_LIMITED`.
- [ ] `now >= endsAt`이거나 TTL 안의 ended session이면 batch를 반영하지 않고 `200 OK`, `accepted = false`, `ignoredReason = "ROUND_ENDED"`를 반환한다.
- [ ] accepted batch마다 참가자별 count, total count, ranking, `stateVersion`을 갱신한다.
- [ ] total이 100 이상이면 `colorChanged = true`.
- [ ] 성공/중복/종료 응답 모두 submit 응답 스키마를 유지한다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.application.usecase.SubmitBurstGameTapUseCaseTest
./gradlew test --tests com.team2.server.burstgame.api.BurstGameControllerTest
```

## Task 8: 상태/결과 조회 UseCase와 API 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/usecase/GetBurstGameSnapshotUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/dto/BurstGameStateResponse.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/application/usecase/GetBurstGameSnapshotUseCaseTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`

- [ ] `GET /api/v1/parties/{partyId}/burst-game`을 추가한다.
- [ ] `BurstGameParticipantResolver`를 재사용해 호출자를 식별하고 `myTapCount`를 채운다.
- [ ] party 기준으로 active 또는 TTL 안의 ended session을 조회한다.
- [ ] session이 없으면 `BURST_GAME_NOT_FOUND`.
- [ ] active session이고 `now >= endsAt`이면 lazy 종료를 수행한 뒤 ended 결과를 반환한다.
- [ ] active 상태에는 `ended = false`, entry 기준 상위 3명 ranking, `myTapCount`를 포함한다.
- [ ] ended 결과에는 `ended = true`, 최종 total, 전체 rankings, `myTapCount`를 포함한다.
- [ ] 응답에는 `endedAt`을 포함하지 않는다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.application.usecase.GetBurstGameSnapshotUseCaseTest
./gradlew test --tests com.team2.server.burstgame.api.BurstGameControllerTest
```

## Task 9: SSE 이벤트 브로드캐스트와 throttle 구현

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcaster.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSseGateway.kt`
- Modify if needed: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/SseEmitterRegistry.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseBurstGameEventBroadcasterTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`

- [ ] `BurstGameEventBroadcaster` 인터페이스 구현체를 추가한다.
- [ ] `SseBurstGameEventBroadcaster`는 `PartySseEventPublisher.broadcastAfterCommit(...)` 계약을 통해 기존 파티 SSE 채널에 이벤트를 발행한다.
- [ ] `ChatSseGateway`는 `PartySseEventPublisher`를 구현하고, `burstgame`은 `chat.infrastructure.sse.ChatSseGateway`를 직접 참조하지 않는다.
- [ ] 기존 파티 SSE 연결에 `burst-game-started` 이벤트를 보낸다.
- [ ] accepted tap batch 이후 `burst-game-progress` 이벤트를 보낸다.
- [ ] progress 이벤트에는 `colorChanged`, `endsAt`, `stateVersion`, `serverTime`, entry 기준 상위 3명 `rankings`를 포함한다.
- [ ] progress countdown 기준은 서버가 내려준 `endsAt`이다.
- [ ] progress는 party/round 단위 200~300ms trailing throttle을 적용한다.
- [ ] throttle 구현은 `ConcurrentHashMap<RoundId, ScheduledFuture>`와 `ScheduledExecutorService` 기반 또는 동등한 trailing throttle 메커니즘으로 둔다.
- [ ] throttle 구간의 중간 `stateVersion` 누락을 허용한다.
- [ ] 종료 이벤트는 throttle과 무관하게 반드시 `burst-game-ended`로 발화한다.
- [ ] end 이벤트는 마지막 progress보다 큰 `stateVersion`을 가질 수 있다.
- [ ] broadcast snapshot 생성은 lock 안에서, 실제 SSE emit은 lock 밖 비동기 executor에서 수행한다.
- [ ] SSE emit 실패는 session 상태에 영향을 주지 않는다.

Run:

```bash
./gradlew test --tests com.team2.server.burstgame.infrastructure.realtime.SseBurstGameEventBroadcasterTest
./gradlew test --tests com.team2.server.chat.infrastructure.sse.SseEmitterRegistryTest
```

## Task 10: Swagger와 에러 응답 정리

**Files:**
- Modify: `src/main/kotlin/com/team2/server/burstgame/api/BurstGameApi.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Test: `src/test/kotlin/com/team2/server/common/config/SwaggerConfigTest.kt`

- [ ] `BURST_GAME_NOT_FOUND`, `BURST_GAME_ALREADY_ENDED`, `BURST_GAME_NOT_READY`, `BURST_GAME_RATE_LIMITED`를 추가한다.
- [ ] start API의 200/400/401/404/409/500 예시를 문서화한다.
- [ ] submit API의 200/400/401/404/429/500 예시를 문서화한다.
- [ ] 상태/결과 조회 API의 200/401/404/500 예시를 문서화한다.
- [ ] `X-Participant-Token` 헤더 사용 가능성을 API 문서에 명시한다.

Run:

```bash
./gradlew test --tests com.team2.server.common.config.SwaggerConfigTest
```

---

## Test Plan

### UseCase 단위 테스트

- start: 실시간 파티가 아니면 `CHAT_NOT_SUPPORTED`
- start: 프로필 없는 호출자는 `UNAUTHORIZED`
- start: 촛불끄기가 완료되지 않았으면 `BURST_GAME_NOT_READY`
- start: active round가 있으면 기존 party 상태 반환
- start: ended round가 있으면 `BURST_GAME_ALREADY_ENDED`
- start: 실시간 파티 참여자는 누구나 호출 가능
- start: 동시에 여러 명이 호출해도 partyId 단위 직렬화로 active session은 1개만 생성됨
- start: active round가 이미 있으면 촛불끄기 완료 상태를 재검증하지 않고 기존 active 상태 반환
- submit: TTL 안의 ended session이면 `200 OK`, `accepted = false`, `ignoredReason = "ROUND_ENDED"`
- submit: TTL 만료 또는 해당 파티에 session이 없으면 `BURST_GAME_NOT_FOUND`
- submit: 요청 `tapCount`가 0 또는 31이면 `INVALID_INPUT`
- submit: 같은 `clientSequence` 재요청은 `200 OK`, `accepted = false`, count 증가 없음
- submit: `MAX_SEQUENCE_GAP` 이하의 sequence gap은 허용하고 늦게 도착한 미처리 sequence도 반영
- submit: `clientSequence > maxAcceptedSequence + MAX_SEQUENCE_GAP`이면 `INVALID_INPUT`
- submit: 이미 처리한 `clientSequence`만 duplicate로 무시
- submit: 참가자별 rate limit 초과 시 `BURST_GAME_RATE_LIMITED`
- submit: total이 100 이상이면 `colorChanged = true`
- submit: accepted batch마다 `stateVersion` 증가
- submit: ranking은 tap count desc로 정렬됨
- submit: 공동 rank 내에서는 participant id asc로 표시 순서를 결정
- submit: 동시 요청에서도 total/ranking/stateVersion이 같은 session snapshot 기준으로 생성됨
- submit: `now >= endsAt`이면 lazy 종료가 먼저 수행되고 tap batch는 반영되지 않음
- submit: lazy 종료 응답은 `accepted = false`, `ignoredReason = "ROUND_ENDED"`를 반환하고 최종 전체 순위는 상태/결과 조회 또는 종료 이벤트에서 확인
- state/result: active 라운드 현재 상태 조회 가능
- state/result: ended 라운드가 TTL 안에 있으면 최종 결과 조회 가능
- state/result: active session이지만 `now >= endsAt`이면 lazy 종료 후 ended 결과 반환
- state/result: 외부 응답에 `endedAt`이 포함되지 않음
- state/result: active 상태는 `ended = false`, ended 결과는 `ended = true`로 구분
- state/result: ended 결과에서는 최종 total과 전체 참가자 `rankings`를 포함함
- end: 최종 결과가 ended session에 TTL 동안 유지됨
- end: scheduler와 lazy 종료가 동시에 실행돼도 종료 처리는 한 번만 commit됨
- SSE: progress 이벤트에 `stateVersion`, `serverTime` 포함
- SSE: progress 이벤트는 countdown 계산 기준으로 `endsAt`을 포함한다.
- SSE: stale `stateVersion` 이벤트를 무시할 수 있음
- SSE: throttle 때문에 progress `stateVersion`은 연속되지 않을 수 있음
- ranking: 참여자가 3명 미만이면 `rankings`도 3개보다 적음
- ranking: 1등 2명, 다음 참가자 2등이면 `rankings`는 `[1등, 1등, 2등]`
- ranking: 진행 중 1등 5명이면 `rankings`는 공동 1등 3명만 포함
- ranking: 진행 중 1등 2명, 2등 4명이면 `rankings`는 rank 1 참가자 2명과 rank 2 참가자 1명만 포함
- ranking: 진행 중 1등 3명, 다음 rank group이 2등이면 `rankings`는 rank 1 참가자 3명만 포함
- ranking: 종료 결과는 상위 제한 없이 전체 참가자 순위를 포함
- ranking: 전원 0회면 `rankings = []`
- policy: `BurstGamePolicy.COLOR_CHANGE_TAP_COUNT = 100` 상수 기준으로 `colorChanged` 테스트
- SSE: 실제 emit은 lock 밖 비동기 executor에서 수행되고 실패해도 session 상태는 유지됨

### Controller 통합 테스트

- JWT 참여자 start 성공
- participantToken 참여자 start 성공
- start API는 요청 body 없이 성공
- active round가 있을 때 start 재호출은 같은 party 상태 반환
- tap batch 제출 후 submit response payload 형태 검증
- tap batch 제출 후 SSE progress payload 형태 검증
- 20초 종료는 테스트에서 clock/scheduler를 제어해 `burst-game-ended` 검증
- 상태/결과 조회 API는 진행 중 현재 total/ranking 반환
- 상태/결과 조회 API는 종료 후 TTL 안의 최종 total count와 전체 rankings를 반환함
- 상태/결과 조회 API는 `burst-game-started` 이벤트를 놓친 호출자가 partyId만으로 복구 가능

---

## Verification

```bash
./gradlew test
./gradlew ktlintCheck
```
