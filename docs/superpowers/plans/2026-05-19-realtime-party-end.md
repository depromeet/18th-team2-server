# Realtime Party End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티의 주최자 수동 종료, 10분 자동 종료, 60초 종료 카운트다운, SSE 종료 이벤트, 종료 후 롤링페이퍼 이동 정보를 구현한다.

**Architecture:** 파티 상태/권한/복구 API는 `party/application/usecase` 중심으로 구현하고, SSE 발송과 polling은 기존 `chat/infrastructure/sse` 흐름을 확장한다. 실시간 세션 종료는 `Party.endedAt()`과 분리해 `RealtimeParty.liveEndingStartedAt`으로 관리한다.

**Spec Reference:** `docs/superpowers/specs/2026-05-19-realtime-party-end-design.md`

---

## Decisions

- 단일 애플리케이션 인스턴스 운영을 전제로 한다.
- 종료 시작 시각만 DB에 저장한다. 종료 완료 시각은 `liveEndingStartedAt + 60초`로 계산한다.
- 종료 원인 컬럼은 추가하지 않는다.
- 자동 종료는 `startedAt + 10분`을 `liveEndingStartedAt`에 저장한다. 스케줄러 실행 시각 `now`를 저장하지 않는다.
- 주최자 롤링페이퍼 열람 가능 시점은 `liveEndingStartedAt ?: startedAt + 10분`이다.
- `party-ended`는 공통 payload만 보내고, 개인화 이동 정보는 `GET /api/v1/parties/{partyId}/realtime-next-action`에서 조회한다.
- `PartyEndScheduler`는 per-party 예약을 잡지 않고 DB polling으로 `party-ending`, `party-ended`를 발송한다.
- 비회원 참가자의 `rollingPaperWritten`은 `participantToken -> RealtimeParticipantProfile -> Participant.hasWrittenPaper`로 계산한다.
- 참가자용 `inviteToken`은 해당 party의 만료되지 않은 초대 토큰을 `PartyInviteRepository.findByPartyIdAndExpiresAtAfter(partyId, now)`로 조회한다.
- 박터뜨리기 종료는 수동 종료 가능 상태만 열고 자동 종료는 시작하지 않는다.

---

## File Structure

### 신규/수정 예정

| 경로 | 책임 |
|---|---|
| `src/main/resources/db/migration/V4__add_realtime_party_end_state.sql` | `realtime_party.live_ending_started_at` 추가 |
| `src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt` | 종료 상수, 상태 계산, `hostViewableAt()` 수정 |
| `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt` | 종료 polling/조건부 update 쿼리 |
| `src/main/kotlin/com/team2/server/party/api/PartyApi.kt` | 실시간 종료 API Swagger 계약 |
| `src/main/kotlin/com/team2/server/party/api/PartyController.kt` | 주최자 종료 조회/요청, 상태/다음 행동 조회 |
| `src/main/kotlin/com/team2/server/party/api/dto/*Realtime*` | 종료 상태/요청/다음 행동 응답 DTO |
| `src/main/kotlin/com/team2/server/party/application/usecase/*Realtime*` | 종료 가능 조회, 종료 요청, 상태 복구, 다음 행동 조회 |
| `src/main/kotlin/com/team2/server/chat/infrastructure/sse/PartyEndScheduler.kt` | DB polling 기반 종료 이벤트 발송 |
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
- Test: domain/entity or service test for realtime status

- [ ] `realtime_party.live_ending_started_at DATETIME NULL` 컬럼을 추가한다.
- [ ] `RealtimeParty.HOST_END_AVAILABLE_AFTER_MINUTES = 4`를 추가한다.
- [ ] `RealtimeParty.LIVE_END_COUNTDOWN_SECONDS = 60`을 추가한다.
- [ ] `RealtimePartyStatus.LIVE_ENDING`을 추가한다.
- [ ] `effectiveEndingStartedAt()`은 `liveEndingStartedAt ?: startedAt.plusMinutes(LIVE_DURATION_MINUTES)`로 계산한다.
- [ ] `effectiveLiveEndedAt()`은 `effectiveEndingStartedAt().plusSeconds(LIVE_END_COUNTDOWN_SECONDS)`로 계산한다.
- [ ] `status(now)`는 `LIVE_OPEN`, `LIVE_ENDING`, `LIVE_CLOSED`, `ROLLING_PAPER_CLOSED` 순서를 정확히 반영한다.
- [ ] `hostViewableAt()`은 `liveEndingStartedAt ?: startedAt.plusMinutes(LIVE_DURATION_MINUTES)`로 변경한다.

Run:

```bash
./gradlew test --tests '*RealtimeParty*'
```

## Task 2: 종료 시작 조건부 update와 조회 기반 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt`
- Create/Modify: realtime end usecase/service classes under `party/application/usecase`

- [ ] 수동 종료용 조건부 update를 추가한다: `live_ending_started_at = now` only when null.
- [ ] 자동 종료용 조건부 update를 추가한다: `live_ending_started_at = startedAt + 10분` only when null and `startedAt + 10분 <= now`.
- [ ] polling 대상 조회를 추가한다: `liveEndingStartedAt != null`인 realtime party 목록.
- [ ] 종료 가능 조회는 주최자 권한, `REALTIME` 타입, 4분 경과 또는 박터뜨리기 종료 여부를 검증한다.
- [ ] 종료 가능 조회의 `canEnd`는 `liveEndingStartedAt == null && (now >= startedAt + 4분 || 박터뜨리기 종료)`일 때만 true다.
- [ ] 박터뜨리기 완료 상태는 이벤트/상태 provider로 분리해 연결하고, 도메인이 없으면 false를 기본값으로 둔다.
- [ ] 종료 요청은 `LIVE_CLOSED`를 먼저 거부하고 `REALTIME_PARTY_ALREADY_ENDED`를 반환한다.
- [ ] 이미 `LIVE_ENDING`이면 수동 종료 가능 조건을 다시 검사하지 않고 기존 `endingStartedAt`, `endedAt`을 반환한다.
- [ ] `LIVE_OPEN`일 때만 수동 종료 가능 조건을 검사하고, 실패하면 `REALTIME_PARTY_END_NOT_AVAILABLE`을 반환한다.

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

- [ ] `GET /api/v1/parties/{partyId}/realtime-end`를 추가한다. 인증된 주최자만 호출한다.
- [ ] `POST /api/v1/parties/{partyId}/realtime-end`를 추가한다. 인증된 주최자만 호출한다.
- [ ] `GET /api/v1/parties/{partyId}/realtime-state`를 추가한다. `Authorization` 또는 `X-Participant-Token` 중 하나로 파티 소속을 확인한다.
- [ ] `GET /api/v1/parties/{partyId}/realtime-next-action`을 추가한다. `LIVE_CLOSED`에서만 성공한다.
- [ ] `realtime-state`, `realtime-next-action`은 participant token 사용을 위해 path-specific permitAll로 열고, invalid Bearer token은 기존 JWT 정책대로 401을 유지한다.
- [ ] 주최자 next action은 `{ type: "HOST_ROLLING_PAPER_LIST", partyId }`로 응답한다.
- [ ] 참가자 next action은 `{ type: "PARTICIPANT_ROLLING_PAPER_WRITE", inviteToken, rollingPaperWritten }`로 응답한다.
- [ ] 회원 참가자는 `ParticipantRepository.findByPartyIdAndUserId(...)`로 participant를 찾는다.
- [ ] 비회원 참가자는 `RealtimeParticipantProfileRepository.findByParticipantToken(...)`로 profile과 participant를 찾는다.
- [ ] 참가자 `inviteToken`은 `PartyInviteRepository.findByPartyIdAndExpiresAtAfter(partyId, now)`로 조회한다.
- [ ] 참가자 `rollingPaperWritten`은 resolved participant의 `hasWrittenPaper`로 응답한다.
- [ ] `REALTIME_PARTY_END_NOT_AVAILABLE(400)`, `REALTIME_PARTY_ALREADY_ENDED(409)`를 추가한다.

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
- [ ] 주최자 emitter를 식별하거나 owner participant를 통해 `host-end-available`을 주최자에게만 보낼 수 있게 한다.
- [ ] `party-ended` 발송 후 즉시 complete하지 않고 grace time 후 남은 emitter를 정리한다.

Run:

```bash
./gradlew test --tests '*Chat*'
```

## Task 5: PartyEndScheduler를 DB polling으로 변경

**Files:**
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/PartyEndScheduler.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/infrastructure/sse/ChatSchedulerConfig.kt` if needed
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt`
- Test: scheduler/usecase tests

- [ ] 기존 `WARN_BEFORE_END_MINUTES`와 `startedAt + 9분` 알림 예약을 제거한다.
- [ ] `scheduleIfNeeded(...)` 기반 per-party 예약을 제거하거나 no-op로 대체한다.
- [ ] 1초 주기 polling으로 자동 종료 시작 조건을 처리한다.
- [ ] 자동 종료 시작 시 `liveEndingStartedAt = startedAt + 10분`으로 저장한다.
- [ ] `endingNotifiedPartyIds` 메모리 Set으로 `party-ending` 중복 발송을 막는다.
- [ ] `endedNotifiedPartyIds` 메모리 Set으로 `party-ended` 중복 발송을 막는다.
- [ ] `liveEndingStartedAt + 60초 <= now`이면 `party-ended`를 발송하고 grace cleanup을 예약한다.
- [ ] 재시작 후 `party-ending` 누락은 재연결 `party-state`로 복구하고, polling은 현재 DB 상태 기준으로 계속 동작한다.

Run:

```bash
./gradlew test --tests '*PartyEndScheduler*'
```

## Task 6: 박터뜨리기 종료 이벤트 연결

**Files:**
- Create/Modify burst game event type when the burst game module is available
- Create listener/usecase in party or chat boundary

- [ ] 박터뜨리기 종료 시 `BurstGameEndedEvent(partyId)`를 발행한다.
- [ ] 이벤트 listener는 해당 party가 realtime이고 아직 `LIVE_OPEN`이면 주최자에게 `host-end-available`을 보낸다.
- [ ] 이 이벤트는 `liveEndingStartedAt`을 저장하지 않는다.
- [ ] 이벤트가 먼저 오고 4분도 경과하면 `realtime-end` 조회는 `canEnd = true`를 반환한다.

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
  - 4분 전 수동 종료 요청 실패
  - 4분 경과 후 수동 종료 성공
  - 박터뜨리기 종료 후 4분 전에도 수동 종료 성공
  - 소유자가 아니면 403
  - `PAPER_ONLY`이면 `CHAT_NOT_SUPPORTED`
  - 이미 `LIVE_ENDING`이면 기존 종료 시각 반환
  - `LIVE_ENDING` 재요청은 4분/박터뜨리기 조건을 다시 검사하지 않음
  - 이미 `LIVE_CLOSED`이면 `REALTIME_PARTY_ALREADY_ENDED`
  - `liveEndingStartedAt`이 있으면 `realtime-end` 조회의 `canEnd = false`
  - 동시 요청에서 하나만 update 성공

- 복구/다음 행동 API
  - 주최자 `realtime-end` 복구 조회 성공
  - 회원 participant `realtime-state` 조회 성공
  - 비회원 participant token으로 `realtime-state` 조회 성공
  - `LIVE_OPEN`, `LIVE_ENDING`에서 `realtime-next-action` 실패
  - `LIVE_CLOSED`에서 주최자 next action 응답
  - `LIVE_CLOSED`에서 참가자 next action 응답
  - participant token이 다른 파티 소속이면 403

- SSE / scheduler
  - 연결 직후 `party-state` 전송
  - 4분 도달 시 주최자에게 `host-end-available`
  - 자동 종료 시작 시 `party-ending`
  - 자동 종료 저장값은 polling 실행 시각이 아니라 `startedAt + 10분`
  - 60초 경과 후 `party-ended`
  - `party-ended` 후 grace cleanup
  - `LIVE_ENDING`에서 기존 participantToken 재연결 허용
  - `LIVE_CLOSED`에서 SSE 재연결 거부

## Verification

```bash
./gradlew test --tests '*RealtimeParty*'
./gradlew test --tests '*RealtimePartyEnd*'
./gradlew test --tests '*PartyEndScheduler*'
./gradlew test --tests '*Chat*'
./gradlew test
./gradlew ktlintCheck
```
