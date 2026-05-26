# Realtime Party End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티의 주최자 수동 종료, 10분 자동 종료, 60초 종료 카운트다운, SSE 종료 이벤트, 종료 후 롤링페이퍼 이동 정보를 구현한다.

**Architecture:** 파티 상태/권한/복구 API는 `party/application/usecase` 중심으로 구현하고, SSE 발송과 예약 실행은 기존 `chat/infrastructure/sse` 흐름을 확장한다. 실시간 세션 종료는 `Party.endedAt()`과 분리해 `RealtimeParty.liveEndingStartedAt`으로 관리한다.

**Architecture Test Guardrails:** `*Controller`는 `..api..`, `*UseCase`는 `..application.usecase..`, `*Repository`는 `..infrastructure.persistence..`에 둔다. `@Transactional`은 UseCase에만 둔다. `chat` 인프라가 `party.infrastructure.persistence`를 직접 의존하지 않도록, DB 조회/조건부 update는 `party.application.usecase` 경유로 노출한다.

**Test Guardrails:** MockMvc 통합 테스트는 `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration::class)` 조합을 사용한다. 단순 통합 테스트는 `IntegrationTestSupport`, JPA slice는 `JpaSliceTestSupport`를 상속한다. 새 테스트에서 `@MockBean`, `@SpyBean`, `@TestPropertySource`, 임의 `@ActiveProfiles`를 추가해 Spring context fingerprint를 늘리지 않는다.

**Spec Reference:** `docs/superpowers/specs/2026-05-19-realtime-party-end-design.md`

---

## Decisions

- 단일 애플리케이션 인스턴스 운영을 전제로 한다.
- 종료 시작 시각만 DB에 저장한다. 종료 완료 시각은 `liveEndingStartedAt + 60초`로 계산한다.
- 종료 원인 컬럼은 추가하지 않는다.
- 자동 종료는 `startedAt + 10분`을 `liveEndingStartedAt`에 저장한다. 스케줄러 실행 시각 `now`를 저장하지 않는다.
- 주최자 롤링페이퍼 열람 가능 시점은 `liveEndingStartedAt ?: startedAt + 10분`이다.
- `party-ended`는 공통 payload만 보내고, 개인화 이동 정보는 `GET /api/v1/parties/{partyId}/realtime-next-action`에서 조회한다.
- `PartyEndScheduler`는 1초 반복 polling을 사용하지 않고, startup recovery + after-commit event + `TaskScheduler` 예약으로 `party-ending`, `party-ended`를 발송한다.
- 비회원 참가자의 `rollingPaperWritten`은 `participantToken -> RealtimeParticipantProfile -> Participant.hasWrittenPaper`로 계산한다.
- 참가자용 `inviteToken`은 `PartyInviteService`를 통해 조회한다. 요청 초대 토큰이 없으면 해당 party의 만료되지 않은 초대 토큰 중 최신 1개를 선택한다.
- 주최자는 `LIVE_OPEN` 동안 별도 시간 제한이나 박터뜨리기 종료 조건 없이 수동 종료할 수 있다.
- 박터뜨리기 종료는 실시간 파티 종료 가능 여부에 영향을 주지 않는다.

---

## File Structure

### 신규/수정 예정

| 경로 | 책임 |
|---|---|
| `src/main/resources/db/migration/V4__add_realtime_party_end_state.sql` | `realtime_party.live_ending_started_at` 추가 |
| `src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt` | 종료 상수, 상태 계산, `hostViewableAt()` 수정 |
| `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt` | 종료 복구 조회/조건부 update 쿼리 |
| `src/main/kotlin/com/team2/server/party/api/PartyApi.kt` | 실시간 종료 API Swagger 계약 |
| `src/main/kotlin/com/team2/server/party/api/PartyController.kt` | 주최자 종료 요청, 상태/다음 행동 조회 |
| `src/main/kotlin/com/team2/server/party/api/dto/*Realtime*` | 종료 상태/요청/다음 행동 응답 DTO |
| `src/main/kotlin/com/team2/server/party/application/usecase/*Realtime*` | 종료 요청, 상태 복구, 다음 행동 조회 |
| `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt` | startup recovery + event 기반 종료 이벤트 발송 |
| `src/main/kotlin/com/team2/server/chat/infrastructure/sse/SseEmitterRegistry.kt` | 주최자 단독 알림, grace cleanup 지원 |
| `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt` | 연결 직후 `party-state` 발송, `LIVE_ENDING` 재연결 허용 |
| `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` | participant token 기반 복구 API 공개 경로 조정 |
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | 실시간 종료 전용 에러 추가 |

---

## Task Order

## Task 1: 도메인 상태와 migration 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt`
- Create: `src/main/resources/db/migration/V4__add_realtime_party_end_state.sql`
- Test: domain/entity test for realtime status
- Test: `src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt`

- [ ] `realtime_party.live_ending_started_at DATETIME NULL` 컬럼을 추가한다.
- [ ] `RealtimeParty.LIVE_END_COUNTDOWN_SECONDS = 60`을 추가한다.
- [ ] `RealtimePartyStatus.LIVE_ENDING`을 추가한다.
- [ ] `effectiveEndingStartedAt()`은 `liveEndingStartedAt ?: startedAt.plusMinutes(LIVE_DURATION_MINUTES)`로 계산한다.
- [ ] `effectiveLiveEndedAt()`은 `effectiveEndingStartedAt().plusSeconds(LIVE_END_COUNTDOWN_SECONDS)`로 계산한다.
- [ ] `status(now)`는 `ROLLING_PAPER_CLOSED`, `ROLLING_PAPER_OPEN`, `LIVE_OPEN`, `LIVE_ENDING`, `LIVE_CLOSED` 우선순서로 평가한다.
- [ ] `hostViewableAt()`은 `liveEndingStartedAt ?: startedAt.plusMinutes(LIVE_DURATION_MINUTES)`로 변경한다.
- [ ] `FlywayMigrationTest`로 V4 migration이 clean DB에 적용되는지 검증한다.

Run:

```bash
./gradlew test --tests '*RealtimeParty*'
./gradlew test --tests com.team2.server.db.FlywayMigrationTest
```

## Task 2: 종료 시작 조건부 update와 조회 기반 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt`
- Create/Modify: realtime end usecase/service classes under `party/application/usecase`

- [ ] 수동 종료용 조건부 update를 추가한다: `live_ending_started_at = now` only when null.
- [ ] 자동 종료용 조건부 update를 추가한다: `live_ending_started_at = startedAt + 10분` only when null and `startedAt + 10분 <= now`.
- [ ] startup recovery용 조회를 추가한다: 자동 종료 예약 대상과 이미 종료 countdown이 시작된 realtime party 목록.
- [ ] `PartyEndScheduler`가 repository를 직접 주입받지 않도록 복구/조건부 종료 UseCase를 제공한다.
- [ ] 종료 요청은 `LIVE_CLOSED`를 먼저 거부하고 `REALTIME_PARTY_ALREADY_ENDED`를 반환한다.
- [ ] 이미 `LIVE_ENDING`이면 기존 `endingStartedAt`, `endedAt`을 반환한다.
- [ ] `LIVE_OPEN`이면 주최자 권한만 확인하고 별도 unlock 조건 없이 종료 카운트다운을 시작한다.
- [ ] 시작 전 등 `LIVE_OPEN`이 아닌 상태에서 종료 요청하면 `REALTIME_PARTY_INVALID_STATE`를 반환한다.

Run:

```bash
./gradlew test --tests '*RealtimePartyEnd*'
```

## Task 3: 종료/복구/다음 행동 API 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/api/PartyApi.kt`
- Modify: `src/main/kotlin/com/team2/server/party/api/PartyController.kt`
- Create: DTOs under `src/main/kotlin/com/team2/server/party/api/dto/`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Test: party controller tests

- [ ] `POST /api/v1/parties/{partyId}/realtime-end`를 추가한다. 인증된 주최자만 호출한다.
- [ ] `GET /api/v1/parties/{partyId}/realtime-state`를 추가한다. `Authorization` 또는 `X-Participant-Token` 중 하나로 파티 소속을 확인한다.
- [ ] `GET /api/v1/parties/{partyId}/realtime-next-action`을 추가한다. `LIVE_CLOSED`에서만 성공한다.
- [ ] `realtime-state`, `realtime-next-action`은 participant token 사용을 위해 path-specific permitAll로 열고, invalid Bearer token은 기존 JWT 정책대로 401을 유지한다.
- [ ] 주최자 next action은 `{ type: "HOST_ROLLING_PAPER_LIST", partyId }`로 응답한다.
- [ ] 참가자 next action은 `{ type: "PARTICIPANT_ROLLING_PAPER_WRITE", inviteToken, rollingPaperWritten }`로 응답한다.
- [ ] 회원 참가자는 `ParticipantService`를 통해 party 소속 participant를 찾는다.
- [ ] 비회원 참가자는 `ParticipantService` 또는 `RealtimeParticipantProfileService`를 통해 participant token에 연결된 profile과 participant를 찾는다.
- [ ] 참가자 `inviteToken`은 `PartyInviteService`를 통해 조회한다. 요청 초대 토큰이 있으면 그 값을 우선 사용하고, 없으면 해당 party의 유효 초대 토큰 중 최신 1개를 선택한다.
- [ ] 참가자 `rollingPaperWritten`은 resolved participant의 `hasWrittenPaper`로 응답한다.
- [ ] `REALTIME_PARTY_INVALID_STATE(400)`, `REALTIME_PARTY_ALREADY_ENDED(409)`를 추가한다.
- [ ] 컨트롤러 테스트는 기존 MockMvc 통합 테스트 패턴을 따라 `@Import(TestcontainersConfiguration::class)`를 포함한다.

Run:

```bash
./gradlew test --tests '*PartyControllerTest'
```

## Task 4: SSE registry와 연결 초기 이벤트 확장

**Files:**
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/SseEmitterRegistry.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSseGateway.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt`
- Test: chat/realtime controller or usecase tests

- [ ] SSE 연결 직후 `party-state`를 항상 전송한다.
- [ ] `party-state` payload에 `partyId`, `status`, `liveStartAt`, `endingStartedAt`, `endedAt`을 포함한다.
- [ ] 신규 입장은 `LIVE_OPEN`에서만 허용한다.
- [ ] 기존 participantToken 기반 재연결은 `LIVE_ENDING`에서도 허용한다.
- [ ] `LIVE_CLOSED`에서는 SSE 연결을 거부하고 REST 복구 API 사용을 유도한다.
- [ ] `party-ended` 발송 후 즉시 complete하지 않고 grace time 후 남은 emitter를 정리한다.

Run:

```bash
./gradlew test --tests '*Chat*'
```

## Task 5: PartyEndScheduler를 startup recovery + event 기반으로 변경

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/sse/PartyEndScheduler.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSchedulerConfig.kt` if needed
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt`
- Test: scheduler/usecase tests

- [ ] 기존 `WARN_BEFORE_END_MINUTES`와 `startedAt + 9분` 알림 예약을 제거한다.
- [ ] 앱 시작 시 DB 상태를 1회 조회해 자동 종료 시작 예약과 `party-ended` 예약을 복구한다.
- [ ] 신규 실시간 파티 생성 commit 이후 `RealtimePartyCreatedEvent`로 자동 종료 시작 예약을 잡는다.
- [ ] 자동 종료 예약 task는 조건부 update로 `liveEndingStartedAt = startedAt + 10분`을 저장한다.
- [ ] 수동/자동 종료 시작 commit 이후 `RealtimePartyEndingStartedEvent`로 `party-ending`을 전송하고 `party-ended`를 예약한다.
- [ ] `PartyEndScheduler`는 `party.application.usecase`만 호출하고, `party.infrastructure.persistence`를 직접 참조하지 않는다.
- [ ] 자동 종료 시작 시 `liveEndingStartedAt = startedAt + 10분`으로 저장한다.
- [ ] `endingNotifiedPartyIds` 메모리 Set으로 `party-ending` 중복 발송을 막는다.
- [ ] `endedNotifiedPartyIds` 메모리 Set으로 `party-ended` 중복 발송을 막는다.
- [ ] `liveEndingStartedAt + 60초 <= now`이면 `party-ended`를 발송하고 grace cleanup을 예약한다.
- [ ] 재시작 후 `party-ending` 누락은 재연결 `party-state`로 복구하고, scheduler는 복구된 DB 상태 기준으로 예약을 다시 잡는다.

Run:

```bash
./gradlew test --tests '*PartyEndScheduler*'
```

## Task 6: 박터뜨리기 종료와 실시간 종료 분리 확인

**Files:**
- Modify/remove party-side burst game end listener or provider if present
- Test: realtime party end and burstgame boundary tests

- [ ] 실시간 파티 종료 도메인이 박터뜨리기 완료 여부를 조회하지 않도록 제거한다.
- [ ] 박터뜨리기 종료 이벤트가 `host-end-available` 또는 수동 종료 가능 상태를 만들지 않도록 제거한다.
- [ ] 박터뜨리기 종료 이벤트는 박터뜨리기 결과/상태 이벤트로만 유지한다.

Run:

```bash
./gradlew test --tests '*Burst*' --tests '*RealtimePartyEnd*'
```

## Test Plan

- 도메인 상태
  - 시작 전 `ROLLING_PAPER_OPEN`
  - 시작 후 종료 시작 전 `LIVE_OPEN`
  - 종료 시작 후 60초 전 `LIVE_ENDING`
  - 종료 시작 후 60초 경과 `LIVE_CLOSED`
  - `Party.isEnded(now)` 이후 `ROLLING_PAPER_CLOSED`
  - 수동 종료 시 `hostViewableAt() == liveEndingStartedAt`
  - 수동 종료 없으면 `hostViewableAt() == startedAt + 10분`

- 주최자 종료 API
  - `LIVE_OPEN`이면 4분 경과 전에도 수동 종료 성공
  - 소유자가 아니면 403
  - `PAPER_ONLY`이면 `CHAT_NOT_SUPPORTED`
  - 이미 `LIVE_ENDING`이면 기존 종료 시각 반환
  - 이미 `LIVE_CLOSED`이면 `REALTIME_PARTY_ALREADY_ENDED`
  - 시작 전에는 `REALTIME_PARTY_INVALID_STATE`
  - 동시 요청에서 하나만 update 성공

- 복구/다음 행동 API
  - 회원 participant `realtime-state` 조회 성공
  - 비회원 participant token으로 `realtime-state` 조회 성공
  - `LIVE_OPEN`, `LIVE_ENDING`에서 `realtime-next-action` 실패
  - `LIVE_CLOSED`에서 주최자 next action 응답
  - `LIVE_CLOSED`에서 참가자 next action 응답
  - participant token이 다른 파티 소속이면 403

- SSE / scheduler
  - 연결 직후 `party-state` 전송
  - 자동 종료 시작 시 `party-ending`
  - 자동 종료 저장값은 scheduler 실행 시각이 아니라 `startedAt + 10분`
  - 60초 경과 후 `party-ended`
  - `party-ended` 후 grace cleanup
  - `LIVE_ENDING`에서 기존 participantToken 재연결 허용
  - `LIVE_CLOSED`에서 SSE 재연결 거부

## Verification

```bash
./gradlew test --tests 'com.team2.server.architecture.*'
./gradlew test --tests com.team2.server.db.FlywayMigrationTest
./gradlew test --tests '*RealtimeParty*'
./gradlew test --tests '*RealtimePartyEnd*'
./gradlew test --tests '*PartyEndScheduler*'
./gradlew test --tests '*Chat*'
./gradlew test
./gradlew ktlintCheck
```
