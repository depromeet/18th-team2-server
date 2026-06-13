# 실시간 파티 종료 설계

- 작성일: 2026-05-19
- 기준 브랜치: `develop`
- 운영 전제: 단일 애플리케이션 인스턴스

## 1. 정책

실시간 파티 종료는 실시간 세션의 종료 시점을 관리한다.

- 주최자만 수동 종료 가능
- 수동 종료 가능 조건: `LIVE_OPEN` 상태의 주최자는 언제든 종료 가능
- 주최자 수동 종료 원인은 종료 요청 시점에 `hostEnteredAt + 4분`이 지났거나 박터뜨리기가 종료됐으면 `HOST_REQUEST`, 그 전이면 `HOST_LEFT`
- `hostEnteredAt + 4분`은 파티 예약 시작 시각이 아니라 주최자가 실시간 파티에 처음 입장한 시각을 기준으로 계산
- 종료 요청 시점이 정확히 `hostEnteredAt + 4분`이면 `HOST_REQUEST`
- 박터뜨리기 종료 시각은 영속화하며, 서버 재시작 후 `hostEnteredAt + 4분` 전 종료 요청도 박터뜨리기가 이미 종료됐다면 `HOST_REQUEST`
- `burstGameEndedAt`은 박터뜨리기 최초 종료 시각만 조건부 저장하며 중복 종료 이벤트로 변경하지 않음
- 종료 요청과 박터뜨리기 종료가 경합하면 저장된 사건 시각을 비교한다. `burstGameEndedAt <= 종료 요청 시각`이면 `HOST_REQUEST`, 아니면 `HOST_LEFT`
- 종료 시작 원인은 종료 요청 시점에 확정하며 이후 발생한 사건으로 변경하지 않음
- 종료 요청 시점이 정확히 `startedAt + 10분`이거나 그 이후면 자동 종료 저장 여부와 관계없이 `TIME_LIMIT_REACHED`
- 방어적으로 `hostEnteredAt == null` 상태에서 수동 종료 요청이 처리되면 `HOST_LEFT`
- `hostFarewellAvailable`은 현재 주최자 종료 인사하기 버튼을 사용할 수 있는지 나타낸다. `LIVE_OPEN`이고 `now >= hostEnteredAt + 4분` 또는 `burstGameEndedAt <= now`이면 `true`
- `hostEnteredAt == null`이면 `hostFarewellAvailable`은 `false`
- 상태 복구 조회는 `hostFarewellAvailable`과 프론트 타이머 기준인 `hostFarewellAvailableAt = hostEnteredAt + 4분`을 제공
- 프론트는 서버 시각 기준으로 `hostFarewellAvailableAt` 도달을 계산하고, 박터뜨리기 종료는 기존 `burst-game-ended` SSE로 감지
- 조기 종료 확인 팝업과 버튼 노출 UI는 프론트 책임이며, 백엔드는 종료 요청 시 권한 검증과 종료 원인 판정을 담당
- 자동 종료 트리거: `startedAt + 10분`
- 종료 트리거부터 60초 카운트다운 진행
- 종료 카운트다운 화면은 `HOST_REQUEST`, `HOST_LEFT`, `TIME_LIMIT_REACHED` 세 종료 시작 원인을 구분해 표시
- `party-ending` 전송 후 60초 뒤 `party-ended` 전송
- `party-ended` 전송 후 짧은 grace time 뒤 남은 SSE emitter 정리
- 실시간 세션 종료 후 재오픈, 신규 입장, 채팅 전송 불가
- 주최자 롤링페이퍼 열람 가능 시점은 `liveEndingStartedAt ?: startedAt + 10분`

상수:

```kotlin
RealtimeParty.LIVE_DURATION_MINUTES = 10
RealtimeParty.LIVE_END_COUNTDOWN_SECONDS = 60
```

## 2. 상태 모델

`realtime_party`에 컬럼을 추가한다.

| 컬럼 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `live_ending_started_at` | DATETIME | O | 60초 종료 카운트다운 시작 시각 |
| `live_ending_reason` | VARCHAR | O | 종료 카운트다운 시작 원인. 종료 시작과 함께 원자적으로 저장 |
| `burst_game_ended_at` | DATETIME | O | 박터뜨리기 종료 시각. 수동 종료 원인 판정과 재시작 복구에 사용 |

박터뜨리기 종료 시각 저장:

```sql
UPDATE realtime_party
SET burst_game_ended_at = :endedAt
WHERE id = :partyId
  AND burst_game_ended_at IS NULL
```

종료 완료 시각은 `live_ending_started_at + 60초`로 계산한다.
종료 시작 원인은 종료 카운트다운 시작 시 확정해 `live_ending_reason`에 저장한다.
종료 시작 전에는 두 값이 모두 `null`이고, 종료 시작 후에는 `live_ending_started_at`, `live_ending_reason`이 모두 non-null이어야 한다.
마이그레이션 시 기존 종료 데이터는 판단 가능한 기존 규칙으로 백필한다.

- `live_ending_started_at < started_at + 10분`: `HOST_REQUEST`
- `live_ending_started_at >= started_at + 10분`: `TIME_LIMIT_REACHED`
- 기존 데이터에는 조기 종료 판단 정보가 없으므로 `HOST_LEFT`로 백필하지 않음

```kotlin
enum class RealtimePartyEndingReason {
    HOST_REQUEST,
    HOST_LEFT,
    TIME_LIMIT_REACHED,
}
```

| 종료 시작 원인 | 조건 | 의미 | `hostNickname` |
|---|---|---|---|
| `HOST_REQUEST` | 수동 종료 요청 시 `now >= hostEnteredAt + 4분` 또는 `burstGameEndedAt <= now` | 파티가 충분히 진행된 뒤 주최자가 종료 | 종료를 요청한 주최자의 닉네임 |
| `HOST_LEFT` | 수동 종료 요청 시 `hostEnteredAt == null`이거나 `now < hostEnteredAt + 4분`이고 박터뜨리기가 아직 종료되지 않음 | 파티 진행 초기에 주최자가 종료 | 종료를 요청한 주최자의 닉네임 |
| `TIME_LIMIT_REACHED` | 종료 요청 시점이 `startedAt + 10분` 이상이거나 자동 종료 트리거 실행 | 10분 제한 시간 도달 | 파티 주최자의 닉네임 |

수동 종료 요청과 자동 종료 스케줄러가 경합해도 종료 요청 시점이 `startedAt + 10분` 이상이면 `TIME_LIMIT_REACHED`를 저장한다.
스케줄러 실행이 지연된 상태에서 주최자가 먼저 종료 API를 호출해도 동일하다.
`hostNickname`은 파티 주최자의 표시 닉네임이며 종료 원인과 관계없이 항상 제공한다.
값은 주최자의 `RealtimeParticipantProfile.nickname`을 사용한다. 실시간 파티 생성 시 주최자 프로필이 함께 생성되고 프로필 닉네임은 필수 값이므로, `hostNickname`은 non-null 계약으로 제공한다.
주최자 프로필은 `Participant.isCelebrant = true`인 참가자의 `RealtimeParticipantProfile`로 식별한다. 실시간 파티에는 주최자 프로필이 한 개 존재한다.

종료 표시 정보인 `endingReason`, `hostNickname`은 내부에서 하나의 공통 종료 정보 객체로 계산한다.
종료 요청 API, 상태 복구 API, `party-state`, `party-ending`, `party-ended`는 같은 공통 종료 정보를 사용하고 각 payload에는 평평한 필드로 노출한다.
`party-ended`는 최종 종료 문구에 사용하는 `hostNickname`을 제공한다.
백엔드는 주최자와 참가자에게 동일한 종료 정보를 제공하고, 조회자 역할에 따른 화면 문구 차이는 프론트에서 처리한다.

스케줄러가 사용하는 `RealtimeEndingScheduleTarget`은 `endingReason`, `hostNickname`을 포함한다.
앱 시작 시 스케줄 복구와 종료 이벤트 예약은 같은 종료 표시 정보를 사용한다.

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

종료 인사하기와 조기 종료는 동일한 API를 사용하며 클라이언트는 종료 원인을 전달하지 않는다.
서버는 종료 요청 시점의 `hostFarewellAvailable`을 기준으로 `HOST_REQUEST` 또는 `HOST_LEFT`를 판정한다.

```json
{
  "partyId": 1,
  "endingStartedAt": "2026-05-19T20:06:30",
  "endedAt": "2026-05-19T20:07:30",
  "endingReason": "HOST_REQUEST",
  "hostNickname": "홍길동"
}
```

처리 순서:

1. 파티 존재
2. `partyOption == REALTIME`
3. 요청자가 주최자
4. `LIVE_CLOSED`이면 `REALTIME_PARTY_ALREADY_ENDED`
5. `LIVE_ENDING`이면 현재 `endingStartedAt`, `endedAt` 반환
6. `LIVE_OPEN`이면 조건부 update 실행
7. 그 외 상태이면 `REALTIME_PARTY_INVALID_STATE`
8. affected row 기준으로 신규 시작 또는 진행 중인 카운트다운 반환

동시성:

```sql
UPDATE realtime_party
SET live_ending_started_at = :now,
    live_ending_reason = :endingReason
WHERE id = :partyId
  AND live_ending_started_at IS NULL
```

- affected row `1`: 이번 요청이 카운트다운 시작
- affected row `0`: 이미 시작된 카운트다운 조회 후 반환
- 자동 종료 트리거는 조건부 update로 `startedAt + 10분`을 저장

### 3-2. 실시간 상태 복구 조회

```http
GET /api/v1/parties/{partyId}/realtime-state
Authorization: Bearer {accessToken} 또는 X-Participant-Token: {participantToken}
```

주최자와 참가자 모두 사용한다.

```json
{
  "partyId": 1,
  "status": "LIVE_ENDING",
  "liveStartAt": "2026-05-19T20:00:00",
  "endingStartedAt": "2026-05-19T20:10:00",
  "endedAt": "2026-05-19T20:11:00",
  "endingReason": "TIME_LIMIT_REACHED",
  "hostNickname": "홍길동",
  "hostFarewellAvailable": false,
  "hostFarewellAvailableAt": "2026-05-19T20:04:00",
  "serverNow": "2026-05-19T20:10:00"
}
```

`endingReason`은 `LIVE_ENDING`, `LIVE_CLOSED`에서만 제공하고, 종료 카운트다운 시작 전에는 `null`이다.
`hostNickname`은 종료 원인과 상태에 관계없이 항상 제공한다. 따라서 종료 시작 전 상태 응답은 `endingReason = null`, `hostNickname = 주최자 닉네임`으로 내려준다.
`hostFarewellAvailable`은 응답 시점의 파티 상태 스냅샷이며 현재 조회자가 주최자인지와 관계없이 동일하게 제공한다.
`hostFarewellAvailable`, `hostFarewellAvailableAt`, `serverNow`는 `realtime-state`와 SSE 연결 직후 전송하는 `party-state`에 포함한다. 종료 요청 응답과 phase API에는 포함하지 않는다.
`LIVE_ENDING`, `LIVE_CLOSED`, `ROLLING_PAPER_OPEN`, `ROLLING_PAPER_CLOSED`에서는 `false`다.
`hostEnteredAt == null`이면 `hostFarewellAvailableAt`은 `null`이다.
프론트는 `serverNow`를 기준으로 `hostFarewellAvailableAt`까지 남은 시간을 계산하고, 도달 시 로컬 상태를 `true`로 전환한다.
박터뜨리기 종료 시에는 기존 `burst-game-ended` SSE를 받아 로컬 상태를 즉시 `true`로 전환한다.
프론트는 `isHost && 로컬 hostFarewellAvailable`일 때 주최자 종료 인사하기 버튼을 노출한다.

| 응답 또는 이벤트 | `endingReason` | `hostNickname` |
|---|---|---|
| 주최자 종료 요청 API | non-null | non-null |
| `realtime-state`, `party-state` | nullable | non-null |
| `party-ending` | non-null | non-null |
| `party-ended` | 미포함 | non-null |

| 응답 또는 이벤트 | `hostFarewellAvailable` | `hostFarewellAvailableAt` | `serverNow` |
|---|---|---|---|
| `realtime-state`, `party-state` | non-null | nullable | non-null |
| 주최자 종료 요청 API, phase API, `party-ending`, `party-ended` | 미포함 | 미포함 | 미포함 |

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
data: {"partyId":1,"status":"LIVE_ENDING","liveStartAt":"2026-05-19T20:00:00","endingStartedAt":"2026-05-19T20:10:00","endedAt":"2026-05-19T20:11:00","endingReason":"TIME_LIMIT_REACHED","hostNickname":"홍길동","hostFarewellAvailable":false,"hostFarewellAvailableAt":"2026-05-19T20:04:00","serverNow":"2026-05-19T20:10:00"}
```

### party-ending

```text
event: party-ending
data: {"partyId":1,"endingStartedAt":"2026-05-19T20:10:00","endedAt":"2026-05-19T20:11:00","endingReason":"TIME_LIMIT_REACHED","hostNickname":"홍길동"}
```

### party-ended

공통 payload만 전송한다. 개인화 이동 정보는 `realtime-next-action`에서 조회한다.
`party-ended` payload는 `partyId`, `endedAt`, `hostNickname`을 제공한다.

```text
event: party-ended
data: {"partyId":1,"endedAt":"2026-05-19T20:11:00","hostNickname":"홍길동"}
```

## 5. 스케줄링

`PartyEndScheduler`는 DB를 source of truth로 두고 `TaskScheduler` 예약과 트랜잭션 commit 이후 이벤트로 동작한다. SSE는 트랜잭션 commit 이후 발송한다.

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

참가자 SSE 재연결:

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

1. `realtime_party.live_ending_reason`, `burst_game_ended_at` Flyway migration 및 기존 종료 사유 백필
2. `RealtimePartyEndingReason.HOST_LEFT` 추가
3. 종료 시작 조건부 update에서 `live_ending_started_at`, `live_ending_reason` 원자적 저장
4. 박터뜨리기 최초 종료 시각 조건부 저장
5. 수동 종료 요청 시 `TIME_LIMIT_REACHED → HOST_REQUEST → HOST_LEFT` 우선순위 판정
6. 자동 종료와 스케줄 복구에서 저장된 `live_ending_reason` 사용
7. `realtime-state`, `party-state`에 `hostFarewellAvailable`, `hostFarewellAvailableAt`, `serverNow` 제공
8. 기존 `burst-game-ended` SSE를 프론트 종료 인사하기 버튼 활성화 트리거로 문서화
9. 마이그레이션 백필, 4분 경계, 박터뜨리기 경합, 10분 스케줄러 지연, 서버 재시작 복구 테스트 추가

## 9. 참가자 next action 계산 기준

- 비회원 참가자의 `rollingPaperWritten`은 `participantToken -> RealtimeParticipantProfile -> Participant.hasWrittenPaper`로 계산한다.
- 참가자용 `inviteToken`은 해당 party의 만료되지 않은 초대 토큰 중 가장 최근 생성된 토큰을 사용한다.
  - 구현 시 `PartyInviteService`가 유효 초대 토큰 조회와 선택 기준을 캡슐화한다.
  - 유효 초대 토큰이 여러 개면 `createdAt DESC`, `id DESC` 기준으로 1개를 선택한다.
