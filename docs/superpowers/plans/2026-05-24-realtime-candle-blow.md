# Realtime Candle Blow Implementation Plan

> 단계별로 진행한다. 각 단계가 끝나면 커밋하지 않고 변경 내용과 검증 결과를 공유한다.

**Goal:** 주최자가 실시간 파티에 처음 입장한 시각부터 41초 뒤 공유 촛불 9개를 끄는 단계를 시작하고, 9개 모두 꺼짐 또는 환경별 타임아웃으로 종료한 뒤 기존 박터뜨리기 start API를 열어준다.

**Architecture:** 기존 `burstgame` feature 안에 촛불 phase를 추가한다. HTTP 진입점은 `api`, 흐름/트랜잭션은 `application/usecase`, 공유 상태와 정책은 `domain`, in-memory store/scheduler/SSE adapter는 `infrastructure`에 둔다.

**Spec Reference:** `docs/superpowers/specs/2026-05-24-realtime-candle-blow-design.md`

---

## Decisions

- 촛불끄기는 `REALTIME` 파티에서만 동작한다.
- 시작 시각은 `realtime_party.host_entered_at + 41초`다.
- `host_entered_at`은 주최자가 실시간 파티에 처음 입장할 때 한 번만 저장한다.
- 제한 시간은 기본/개발 환경 5분, 운영 환경 45초다.
- 촛불 수는 9개 고정이고 외부 입력으로 바꾸지 않는다.
- 실시간 파티 참여자라면 누구나 촛불을 끌 수 있다.
- 이미 꺼진 촛불 클릭은 `200 OK` 멱등 응답으로 처리한다.
- 종료 조건은 `ALL_EXTINGUISHED` 또는 `TIMEOUT`이다.
- 촛불 종료 후 박터뜨리기는 자동 시작하지 않는다.
- 다음 버튼을 누른 참여자 중 가장 먼저 도착한 기존 `burst-game/start` 요청이 박터뜨리기 라운드를 생성한다.
- 박터뜨리기 선행 조건은 촛불 `finished`다.
- 촛불 세션 상태는 in-memory로 처리하되, 시작 기준 시각은 DB에 저장해 서버 재시작 후 스케줄 복구가 가능하게 한다.
- 확장성을 고려해 촛불 상태 접근은 `CandleBlowSessionStore` 포트 뒤에 둔다.

---

## Task Order

## Task 1: 문서 계약 확정

**Files:**
- Create: `docs/superpowers/specs/2026-05-24-realtime-candle-blow-design.md`
- Create: `docs/superpowers/plans/2026-05-24-realtime-candle-blow.md`
- Modify: `docs/superpowers/specs/2026-05-14-realtime-burst-game-design.md`
- Modify: `docs/superpowers/plans/2026-05-14-realtime-burst-game.md`

- [x] 촛불 시작/종료 시간 정책을 문서화한다.
- [x] 촛불 끄기 API의 `200 OK` 멱등 정책을 문서화한다.
- [x] 촛불 SSE 이벤트 3종을 문서화한다.
- [x] 박터뜨리기 선행 조건을 `finished` 용어로 정렬한다.

## Task 2: 촛불 domain 모델과 store 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowPolicy.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowSession.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowStatus.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowFinishedReason.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowSnapshot.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleState.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowUpdateResult.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowSessionStore.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/service/CandleBlowService.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/candle/InMemoryCandleBlowSessionStore.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/domain/candle/CandleBlowSessionTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/application/service/CandleBlowServiceTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/candle/InMemoryCandleBlowSessionStoreTest.kt`

- [x] `CANDLE_COUNT = 9`
- [x] `START_DELAY_SECONDS = 41`
- [x] 기본 `duration-seconds = 300`, 운영 `duration-seconds = 45`
- [x] 1차 store는 단일 app instance 전제의 in-memory 구현으로 둔다.
- [x] 추후 store 구현 교체 가능성을 고려해 `CandleBlowSessionStore` 포트 뒤에 구현을 숨긴다.
- [x] `CandleBlowSession` aggregate 상태 전이와 store mutation은 `CandleBlowService`가 담당하고, UseCase는 참여자 검증과 응답 변환 흐름만 조합한다.
- [x] `candleId` 범위 `1..9` 검증
- [x] 이미 꺼진 촛불은 멱등으로 현재 상태만 반환
- [x] 전체 소등 시 `FINISHED / ALL_EXTINGUISHED`
- [x] 종료 시각 도달 시 `FINISHED / TIMEOUT`

## Task 3: 상태 조회/촛불 끄기 API와 UseCase 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/burstgame/api/BurstGameApi.kt`
- Modify: `src/main/kotlin/com/team2/server/burstgame/api/BurstGameController.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/usecase/GetCandleBlowStateUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/usecase/BlowCandleUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/dto/CandleBlowResponse.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/api/BurstGameControllerTest.kt`

- [x] `GET /api/v1/parties/{partyId}/candle-blow`
- [x] `POST /api/v1/parties/{partyId}/candle-blow/candles/{candleId}`
- [x] JWT 또는 `X-Participant-Token` 참여자 검증 재사용
- [x] `hostEnteredAt + 41초` 전 `WAITING` 상태 blow 요청은 `CANDLE_BLOW_NOT_STARTED`
- [x] `FINISHED` 상태 blow 요청은 `200 OK` 멱등 응답
- [x] `GetCandleBlowStateUseCase`, `BlowCandleUseCase`는 `CandleBlowService`에 상태 조회/전이 처리를 위임한다.

## Task 4: scheduler와 SSE 구현

**Files:**
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowEventBroadcaster.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowScheduler.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseCandleBlowEventBroadcaster.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledCandleBlowScheduler.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/scheduler/CandleBlowSessionCleanupScheduler.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/realtime/SseCandleBlowEventBroadcasterTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/scheduler/ScheduledCandleBlowSchedulerTest.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/infrastructure/scheduler/CandleBlowSessionCleanupSchedulerTest.kt`

- [x] `candle-blow-started`
- [x] `candle-blow-progress`
- [x] `candle-blow-ended`
- [x] started/ended 이벤트가 중복 발송되지 않도록 party 단위 직렬화
- [x] 앱 재시작 복구 범위는 기존 party scheduler 패턴과 맞춘다.
- [x] `endsAt + 10분` 이후 인메모리 촛불 세션 cleanup

## Task 5: 박터뜨리기 start 선행 조건 연결

**Files:**
- Modify: `src/main/kotlin/com/team2/server/burstgame/application/port/CandleBlowStatusReader.kt`
- Modify: `src/main/kotlin/com/team2/server/burstgame/application/service/BurstGameSessionService.kt`
- Delete: `src/main/kotlin/com/team2/server/burstgame/infrastructure/candle/CandleBlowStatusReaderStub.kt`
- Delete: `src/main/kotlin/com/team2/server/burstgame/infrastructure/candle/CandleBlowStatusReaderUnavailable.kt`
- Create: `src/main/kotlin/com/team2/server/burstgame/infrastructure/candle/InMemoryCandleBlowStatusReader.kt`
- Test: `src/test/kotlin/com/team2/server/burstgame/application/service/BurstGameSessionServiceTest.kt`

- [x] `isCandleBlowFinished` 용어로 정렬
- [x] `ALL_EXTINGUISHED`, `TIMEOUT` 모두 박터뜨리기 start 가능
- [x] `WAITING`, `ACTIVE`는 `BURST_GAME_NOT_READY`
- [x] active burst game이 이미 있으면 촛불 상태 재검증하지 않음
- [x] 박터뜨리기 start 성공 후 촛불 세션 즉시 제거

## Task 6: 아키텍처/회귀 검증

**Files:**
- Review: `src/test/kotlin/com/team2/server/architecture/ArchUnitConstants.kt`
- Test: 관련 burstgame/candle 테스트

- [x] ArchUnit feature 목록에 `burstgame` 포함 여부 확인
- [x] `./gradlew test --tests '*CandleBlow*'`
- [x] `./gradlew test --tests '*BurstGame*'`
- [x] `./gradlew test --tests 'com.team2.server.architecture.*'`
- [x] 마지막 단계에서만 필요 시 `./gradlew check`

Note: 현 브랜치의 `ArchUnitConstants.FEATURES`에는 아직 `burstgame`이 없지만, 해당 반영은 별도 브랜치에서 처리된 항목이므로 이 브랜치에서는 수정하지 않는다.
