# 실시간 파티 종료 설계

- 작성일: 2026-05-19
- 기준 브랜치: `develop`
- 운영 전제: 단일 애플리케이션 인스턴스

## 1. 정책

실시간 파티 종료는 `Party.endedAt()`과 별개의 실시간 세션 종료이다.

- 주최자만 수동 종료 가능
- 수동 종료 가능 조건: `LIVE_OPEN` 상태의 주최자는 별도 시간 제한이나 박터뜨리기 종료 조건 없이 언제든 종료 가능
- 박터뜨리기 종료는 실시간 파티 종료 가능 여부에 영향을 주지 않음
- 자동 종료 트리거: `startedAt + 10분`
- 종료 트리거 후 즉시 종료하지 않고 60초 카운트다운 진행
- `party-ending` 전송 후 60초 뒤 `party-ended` 전송
- `party-ended` 전송 후 짧은 grace time 뒤 남은 SSE emitter 정리
- 실시간 세션 종료 후 재오픈, 신규 입장, 채팅 전송 불가
- 주최자 롤링페이퍼 열람 가능 시점은 `liveEndingStartedAt ?: startedAt + 10분`

상수:

```kotlin
RealtimeParty.LIVE_DURATION_MINUTES = 10
RealtimeParty.LIVE_END_COUNTDOWN_SECONDS = 60
```

기존 `PartyEndScheduler.WARN_BEFORE_END_MINUTES`와 `startedAt + 9분` 알림 예약은 제거한다.

## 2. 상태 모델

`realtime_party`에 컬럼을 추가한다.

| 컬럼 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `live_ending_started_at` | DATETIME | O | 60초 종료 카운트다운 시작 시각 |

종료 완료 시각은 `live_ending_started_at + 60초`로 계산하고, 종료 원인 컬럼은 두지 않는다.

도메인 계산:

```kotlin
val automaticEndingStartedAt = startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
val effectiveEndingStartedAt = liveEndingStartedAt ?: automaticEndingStartedAt
val effectiveLiveEndedAt = effectiveEndingStartedAt.plusSeconds(RealtimeParty.LIVE_END_COUNTDOWN_SECONDS)

fun liveEndedAt(): LocalDateTime? =
    liveEndingStartedAt?.plusSeconds(RealtimeParty.LIVE_END_COUNTDOWN_SECONDS)

override fun hostViewableAt(): LocalDateTime =
    liveEndingStartedAt ?: startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
```

상태:

아래 순서로 평가한다. `Party.isEnded(now)`가 우선되어야 `LIVE_CLOSED`가 롤링페이퍼 종료 상태를 가리지 않는다.

| 상태 | 조건 |
|---|---|
| `ROLLING_PAPER_CLOSED` | `Party.isEnded(now)` |
| `ROLLING_PAPER_OPEN` | `now < startedAt` |
| `LIVE_OPEN` | `startedAt <= now < effectiveEndingStartedAt` |
| `LIVE_ENDING` | `effectiveEndingStartedAt <= now < effectiveLiveEndedAt` |
| `LIVE_CLOSED` | `effectiveLiveEndedAt <= now` |

## 3. API

### 3-1. 주최자 종료 요청

```http
POST /api/v1/parties/{partyId}/realtime-end
Authorization: Bearer {accessToken}
```

```json
{
  "partyId": 1,
  "endingStartedAt": "2026-05-19T20:06:30",
  "endedAt": "2026-05-19T20:07:30"
}
```

처리 순서:

1. 파티 존재
2. `partyOption == REALTIME`
3. 요청자가 주최자
4. `LIVE_CLOSED`이면 `REALTIME_PARTY_ALREADY_ENDED`
5. `LIVE_ENDING`이면 기존 `endingStartedAt`, `endedAt` 반환
6. `LIVE_OPEN`이면 별도 unlock 조건 없이 조건부 update 실행
7. 그 외 상태이면 `REALTIME_PARTY_INVALID_STATE`
8. affected row 기준으로 신규 시작 또는 기존 카운트다운 반환

동시성:

```sql
UPDATE realtime_party
SET live_ending_started_at = :now
WHERE id = :partyId
  AND live_ending_started_at IS NULL
```

- affected row `1`: 이번 요청이 카운트다운 시작
- affected row `0`: 이미 시작된 카운트다운 조회 후 반환
- 자동 종료 트리거도 조건부 update를 사용하되 저장값은 현재 시각이 아니라 `startedAt + 10분`

### 3-2. 실시간 상태 복구 조회

```http
GET /api/v1/parties/{partyId}/realtime-state
Authorization: Bearer {accessToken} 또는 X-Participant-Token: {participantToken}
```

주최자와 기존 참가자 모두 사용한다.

```json
{
  "partyId": 1,
  "status": "LIVE_ENDING",
  "liveStartAt": "2026-05-19T20:00:00",
  "endingStartedAt": "2026-05-19T20:10:00",
  "endedAt": "2026-05-19T20:11:00"
}
```

### 3-3. 종료 후 다음 행동 조회

```http
GET /api/v1/parties/{partyId}/realtime-next-action
Authorization: Bearer {accessToken} 또는 X-Participant-Token: {participantToken}
```

`LIVE_CLOSED` 진입 후 호출한다. `party-ended`를 못 받은 클라이언트도 `realtime-state`로 `LIVE_CLOSED`를 확인한 뒤 호출할 수 있다.
참가자 응답의 `inviteToken`은 롤링페이퍼 작성 화면 진입 편의를 위해 서버가 선택한 유효 초대 토큰이다.

권한:

- 주최자: `Authorization` 필요
- 회원 참가자: `Authorization` 또는 `X-Participant-Token`
- 비회원 참가자: `X-Participant-Token` 필요
- 파티 소속이 아니면 `PARTY_FORBIDDEN`
- `LIVE_OPEN`, `LIVE_ENDING`에서 호출하면 `REALTIME_PARTY_INVALID_STATE`

주최자:

```json
{
  "type": "HOST_ROLLING_PAPER_LIST",
  "partyId": 1
}
```

참가자:

```json
{
  "type": "PARTICIPANT_ROLLING_PAPER_WRITE",
  "inviteToken": "exampletoken0000",
  "rollingPaperWritten": false
}
```

## 4. SSE 이벤트

### party-state

SSE 연결 직후 항상 현재 상태를 1회 전송한다.

```text
event: party-state
data: {"partyId":1,"status":"LIVE_ENDING","liveStartAt":"2026-05-19T20:00:00","endingStartedAt":"2026-05-19T20:10:00","endedAt":"2026-05-19T20:11:00"}
```

### party-ending

```text
event: party-ending
data: {"partyId":1,"endingStartedAt":"2026-05-19T20:10:00","endedAt":"2026-05-19T20:11:00"}
```

### party-ended

공통 payload만 전송한다. 개인화 이동 정보는 `realtime-next-action`에서 조회한다.

```text
event: party-ended
data: {"partyId":1,"endedAt":"2026-05-19T20:11:00"}
```

## 5. 스케줄링

`PartyEndScheduler`는 1초 반복 polling을 사용하지 않는다. DB는 source of truth로 두고, 평상시에는 `TaskScheduler` 예약과 트랜잭션 commit 이후 이벤트로 동작한다. SSE 발송은 트랜잭션 안에서 직접 수행하지 않는다.

앱 시작 시 1회 복구:

```text
live_ending_started_at IS NULL
AND started_at + 10분 <= now
  -> 조건부 update로 live_ending_started_at = started_at + 10분 저장

live_ending_started_at IS NULL
AND started_at + 10분 > now
  -> started_at + 10분에 자동 종료 시작 예약

live_ending_started_at != null
  -> live_ending_started_at + 60초에 party-ended 예약
```

이미 `live_ending_started_at + 60초 <= now`인 경우에는 즉시 `party-ended` 처리 대상으로 본다.

자동 종료 시작:

```text
TaskScheduler 예약 시각 도달
  -> 조건부 update로 live_ending_started_at = started_at + 10분 저장
  -> commit 이후 RealtimePartyEndingStartedEvent 발행
```

수동 종료 시작:

```text
POST /api/v1/parties/{partyId}/realtime-end
  -> 조건부 update로 live_ending_started_at = now 저장
  -> commit 이후 RealtimePartyEndingStartedEvent 발행
```

신규 실시간 파티 생성:

```text
CreateRealtimePartyUseCase
  -> commit 이후 RealtimePartyCreatedEvent 발행
  -> started_at + 10분에 자동 종료 시작 예약
```

종료 시작 이벤트 처리:

```text
RealtimePartyEndingStartedEvent
  -> party-ending 전송
  -> live_ending_started_at + 60초에 party-ended 예약
  -> party-ended 이후 grace time 뒤 남은 SSE emitter 정리
```

재시작 복구:

- `live_ending_started_at == null && started_at + 10분 > now`: 자동 종료 시작 예약 복구
- `live_ending_started_at == null && started_at + 10분 <= now`: 조건부 update 후 종료 시작 이벤트 처리
- `live_ending_started_at != null`: `party-ended` 예약 복구
- 누락된 `party-ending`은 클라이언트가 재연결 시 `party-state`로 카운트다운 상태를 복구한다.

## 6. 입장/채팅 검증

신규 입장과 채팅 전송은 `LIVE_OPEN`에서만 허용한다.

```kotlin
fun isLiveOpen(now: LocalDateTime): Boolean =
    now >= startedAt && now < effectiveEndingStartedAt
```

기존 참가자 SSE 재연결:

- `participantToken`으로 기존 profile 확인 가능하면 `LIVE_ENDING`에서도 허용
- 연결 직후 `party-state` 전송
- `LIVE_CLOSED`에서는 SSE 재연결하지 않고 `realtime-state` 또는 `realtime-next-action`으로 복구

## 7. 오류 코드

| 상황 | 오류 코드 |
|---|---|
| 파티 없음 | `PARTY_NOT_FOUND` |
| 실시간 파티가 아님 | `CHAT_NOT_SUPPORTED` |
| 주최자가 아님 | `PARTY_FORBIDDEN` |
| 현재 실시간 파티 상태에서 요청할 수 없음 | `REALTIME_PARTY_INVALID_STATE` |
| 이미 종료됨 | `REALTIME_PARTY_ALREADY_ENDED` |

```kotlin
REALTIME_PARTY_INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 실시간 파티 상태에서는 요청할 수 없습니다")
REALTIME_PARTY_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 실시간 파티입니다")
```

## 8. 구현 체크리스트

1. `RealtimeParty.liveEndingStartedAt` 추가
2. Flyway migration 추가
3. `RealtimePartyStatus.LIVE_ENDING` 추가
4. `RealtimeParty.hostViewableAt()` 수정
5. 주최자 종료 요청 API 추가
6. 실시간 상태 복구 API 추가
7. 종료 후 다음 행동 조회 API 추가
8. `party-state`, `party-ending`, `party-ended` SSE 처리
9. `PartyEndScheduler`를 startup recovery + event 기반 예약 구조로 변경
10. 기존 `WARN_BEFORE_END_MINUTES`, 9분 트리거, `host-end-available` 제거
11. 종료 시작 조건부 update 구현
12. `party-ended` 후 grace cleanup 적용
13. 신규 입장/채팅 검증에서 `LIVE_ENDING`, `LIVE_CLOSED` 차단
14. 기존 참가자 `LIVE_ENDING` SSE 재연결 허용
15. 박터뜨리기 종료와 실시간 파티 종료 가능 여부 연결 제거
16. 테스트 추가

## 9. 참가자 next action 계산 기준

- 비회원 참가자의 `rollingPaperWritten`은 `participantToken -> RealtimeParticipantProfile -> Participant.hasWrittenPaper`로 계산한다.
- 참가자용 `inviteToken`은 해당 party의 만료되지 않은 초대 토큰 중 가장 최근 생성된 토큰을 사용한다.
  - 구현 시 `PartyInviteService`가 유효 초대 토큰 조회와 선택 기준을 캡슐화한다.
  - 유효 초대 토큰이 여러 개면 `createdAt DESC`, `id DESC` 기준으로 1개를 선택한다.
