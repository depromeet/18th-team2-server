# 실시간 파티 10분 타이머를 주최자 시작 시점 기준으로 변경

- 이슈: [#247](https://github.com/depromeet/18th-team2-server/issues/247)
- 브랜치: `feature/realtime-party-host-start-timer`

## 문제

라이브 10분이 예약 시각 `startedAt`부터 흐른다. 주최자가 늦게 시작하면 그만큼 파티 시간이 깎인다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 타이머 앵커 | `POST /phase/advance` (ENTRY→MUSIC) 호출 시각 |
| 미시작 방치 | `startedAt + 30분` 마감선에서 자동 종료 |
| 대기 구간 status | `LIVE_OPEN` 유지 (입장·채팅이 가능해야 함) |
| 작별인사 기준 | 주최자 입장 시각 → 파티 시작 시각 + 4분 |
| 컬럼 | `host_entered_at` → `live_started_at` rename |
| 기존 데이터 | `started_at <= NOW()` 인 행만 `started_at`으로 백필 |

**FE 변경 없음** — 웹은 이미 `phase/advance`를 호출하고 종료는 SSE로 받기만 한다.

컬럼을 rename하는 이유: `hostEnteredAt`은 API에 노출되지 않고 유일한 소비처가 `hostFarewellAvailableAt`인데,
그 소비처가 시작 시각을 원한다. 새 컬럼을 추가하면 죽은 컬럼이 남는다.

⚠️ 컬럼 rename은 구버전 앱과 호환되지 않는다. 롤링 배포가 필요하면 "추가 → 다음 배포에서 제거" 2단계로 나눈다.

## 변경 내용

### 1. 도메인 (`RealtimeParty`)

- `hostEnteredAt` 필드를 `liveStartedAt`(`live_started_at`)으로 대체
- 마감선 상수 `START_GRACE_MINUTES = 30` 추가
- `automaticEndingStartedAt()`을 분기 처리 — 시작됐으면 `liveStartedAt + 10분`, 아니면 `startedAt + 30분`
- `hostFarewellAvailableAt`의 기준을 `liveStartedAt`으로 교체

`effectiveEndingStartedAt()` / `status()` / `isLiveOpen()` / `endingReason()` / `hostViewableAt()`은
모두 `automaticEndingStartedAt()`을 경유하므로 코드 변경 없이 새 규칙을 따른다.

`endingReasonForManualRequest`도 코드는 그대로지만, 미시작 파티를 수동 종료하면 `HOST_LEFT`로 기록된다.

### 2. 시작 마킹

ENTRY→MUSIC 전환(CAS)이 성공했을 때만 시작 시각을 기록하고 이벤트를 발행한다.

- `MarkRealtimePartyHostEnteredUseCase` → `MarkRealtimePartyStartedUseCase`로 대체, `RealtimePartyStartedEvent` 발행 추가
- `EnterRealtimePartyUseCase.markHostEnteredIfNeeded`(`:50-58`) 삭제 — 입장 시 더 이상 마킹하지 않는다
- `PartyRepository` / `PartyService`의 `markHostEnteredIfAbsent` → `markLiveStartedIfAbsent`
- `AdvancePartyPhaseUseCase`에 의존 추가 (4개 → 5개, 한도 내)

멱등성은 DB가 보장한다. `live_started_at IS NULL` 조건부 UPDATE라 동시 요청이든 재클릭이든 최초 1회만 성공한다.

### 3. 스케줄러 (`PartyEndScheduler`)

- `onRealtimePartyCreated`: 자동 종료를 `startedAt + 10분` → `startedAt + 30분`(fallback)으로 변경
- `onRealtimePartyStarted` 리스너 신규 추가: `liveStartedAt + 10분`으로 재스케줄

`scheduleAutomaticEnd`가 이미 취소 후 교체하므로(`:112-119`) fallback 태스크는 자동으로 취소된다.
종료 통보가 나간 파티는 기존 `isPartyEndingHandled()` 가드(`:106`)가 막는다.

### 4. 재기동 복구

- `startAutomaticRealtimeEndings` 네이티브 쿼리(`PartyRepository.kt:64-86`)의 종료 시각 계산을
  `COALESCE(live_started_at + 10분, started_at + 30분)` 기준으로 변경
- `RealtimePartyEndService.findRecoverySchedules`(`:72`)의 조회 범위를 `now - (30 + 10)분`으로 확대.
  **놓치면 대기 중이던 파티가 복구 대상에서 빠져 영원히 끝나지 않는다.**

`phaseStore`는 인메모리라 재기동 시 ENTRY로 리셋되지만, `live_started_at`이 DB에 남아 있어
주최자가 시작 버튼을 다시 눌러도 타이머가 밀리지 않는다.

### 5. 마이그레이션 (`V13__rename_host_entered_at_to_live_started_at.sql`)

컬럼을 rename한 뒤(MySQL 8.0의 `RENAME COLUMN`), `started_at`이 이미 지난 행만 `started_at`으로 백필한다.

`WHERE started_at <= NOW()` 조건이 핵심이다. 조건 없이 전체를 채우면 배포 시점에 아직 열리지 않은
미래 예약 파티까지 "이미 시작됨"이 되어, 주최자가 시작 버튼을 눌러도 조건부 UPDATE가 막아 새 기능이 적용되지 않는다.

결과: 끝난 파티와 진행 중이던 파티는 기존 종료 시각을 유지하고, 미래 예약 파티만 새 규칙을 따른다.

### 6. 파급 지점

| 위치 | 변경 |
|---|---|
| `PartyInviteService.kt:76` | 입장 가능 종료 시각을 `effectiveEndingStartedAt()`으로 |
| `LookupPartyInviteUseCase.kt:72` | `liveEndAt`을 `effectiveEndingStartedAt()`으로. `:73` `liveDurationMinutes`는 10 유지 (안내 문구용) |
| `EnterAndSubscribeChatUseCase.kt:117-124` | SSE 타임아웃에 마감선 반영 → 약 46분 (현재 16분) |
| `RealtimePartyEndResults.kt:64` | `hostFarewellAvailableAt` Swagger 설명을 "입장 기준" → "시작 기준" |

**그대로 두는 것:** `GetUpcomingPartiesUseCase.kt:42`의 `hostRollingPaperOpenAt`은 미시작 파티에서
`startedAt + 30분`으로 표시된다. 보수적 상한값이고 파티가 시작되면 정확해지므로,
열람 게이트와 표시값이 어긋나는 것보다 낫다. 같은 파일 `:73`의 `liveEndAt`은 예정 안내용이라 `startedAt + 10분` 유지.

## 테스트

기존 10개 파일이 `hostEnteredAt`을 참조하므로 함께 수정한다 (`burstgame` 3개는 픽스처뿐).

| 대상 | 검증 |
|---|---|
| `RealtimeParty` | `automaticEndingStartedAt()` 분기 · 미시작 시 `hostFarewellAvailableAt`이 null |
| `PartyStatusTest` | 대기 구간이 `LIVE_OPEN` · 마감선 도달 시 `LIVE_ENDING` |
| `AdvancePartyPhaseUseCase` | 마킹 + 이벤트 발행 · 재호출 시 이벤트 미발행 |
| `PartyRepositoryTest` | `markLiveStartedIfAbsent` 멱등성 · 일괄 종료 쿼리 |
| `PartyEndScheduler` | 시작 이벤트 수신 시 fallback 취소 후 재스케줄 |
| 복구 경로 | 대기 중 파티가 `30 + 10분` 조회 범위에 포함되는지 |
| `FlywayMigrationTest` | 컬럼 검증(`:88-91`)을 `live_started_at`으로 · 백필 조건 |

## 기타

`AdvancePartyPhaseUseCase`는 73줄로 팀 규칙(60줄)을 넘겨둔 상태다.
fallback 응답 조립부(`:54-62`)를 private 함수로 추출해 이번 증가분을 흡수한다.
