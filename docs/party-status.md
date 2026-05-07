# 파티 상태 정의

파티 타입별 상태 전이 규칙을 정의한다.

---

## 1. 롤링페이퍼 전용 파티 (`PaperOnlyParty`)

`startedAt: LocalDateTime` — 생일 날짜를 `날짜 00:00`으로 저장 (시간 입력 없음)

| 상태 | 시점 | 참가자 | 주최자 |
|------|------|--------|--------|
| **READY** | 파티 생성 직후 ~ startedAt 전 | 작성 가능, 조회 가능 | 확인 불가 |
| **OPEN** | startedAt ~ startedAt +7일 | 작성 가능, 조회 가능 | 조회 가능 |
| **CLOSED** | startedAt +7일 이후 | 조회만 가능 | 조회만 가능 |

### 상태 전이 조건

```text
READY  : now < startedAt
OPEN   : startedAt ≤ now < startedAt + 7일
CLOSED : now ≥ startedAt + 7일
```

| 상수 | 값 |
|------|----|
| `CLOSED_AFTER_DAYS` | `7` |

---

## 2. 라이브 파티 (`RealtimeParty`)

`startedAt: LocalDateTime` — 라이브 시작 날짜+시간을 저장 (년월일시간 모두 입력)

생성 순간부터 롤링페이퍼 작성 가능. `startedAt` 시각부터 10분간 라이브(채팅) 진행.

| 상태 | 시점 | 기능 |
|------|------|------|
| **ROLLING_PAPER_OPEN** | 파티 생성 직후 ~ 라이브 시작 전 | 롤페 작성·조회 가능 |
| **LIVE_OPEN** | 라이브 시작 ~ +10분 | 라이브 진행(채팅 활성화) + 롤페 작성·조회 가능 |
| **LIVE_CLOSED** | 라이브 종료 후 ~ startedAt +7일 | 롤페 추가 작성·조회 가능 |
| **ROLLING_PAPER_CLOSED** | startedAt +7일 이후 | 롤페 조회만 가능 |

### 상태 전이 조건

```text
ROLLING_PAPER_OPEN   : now < startedAt
LIVE_OPEN            : startedAt ≤ now < startedAt + 10분
LIVE_CLOSED          : startedAt + 10분 ≤ now < startedAt + 7일
ROLLING_PAPER_CLOSED : now ≥ startedAt + 7일
```

| 상수 | 값 |
|------|----|
| `LIVE_DURATION_MINUTES` | `10` |
| `ENDED_AFTER_DAYS` | `7` |

---

## 구현

상태 계산은 각 엔티티의 `status(now: LocalDateTime)` 메서드로 제공한다.

```kotlin
val status: PaperOnlyPartyStatus = paperParty.status()
val status: RealtimePartyStatus  = realtimeParty.status()
```
