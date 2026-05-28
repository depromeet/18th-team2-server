# 실시간 파티 Phase API 설계

- 작성일: 2026-05-26
- 기준 브랜치: `develop`
- 이슈: #185
- 목적: 파티 중간 입장 시 현재 Phase를 반환하고, 모든 참여자가 동일한 화면을 볼 수 있도록 Phase 상태를 추적·브로드캐스트한다.

---

## 0. 빠른 요약

핵심 동작:

- 실시간 파티 내 진행 Phase를 인메모리로 추적한다.
- Phase가 바뀔 때 SSE로 전체 브로드캐스트해 모든 참여자가 같은 화면을 본다.
- Phase 조회 응답에 `phaseStartedAt`과 `serverNow`를 함께 내려 중간 입장자도 정확한 경과 시간을 계산할 수 있다.
- 클라이언트는 자신의 로컬 시계가 아닌 서버 시각(`serverNow`)을 기준으로 타이머를 맞춘다.

Phase 순서:

```
ENTRY → MUSIC → CANDLE → BURST → CLOSEABLE → END
```

---

## 1. Phase 정의

| Phase | 설명 |
|-------|------|
| `ENTRY` | 파티 참여자 입장 단계. 기본값 (스토어에 등록 전) |
| `MUSIC` | 호스트가 "다음"을 눌러 음악이 시작된 단계 |
| `CANDLE` | 음악 종료 후 촛불끄기 단계 |
| `BURST` | 참여자 중 누군가 "다음"을 눌러 박터뜨리기 게임 시작 |
| `CLOSEABLE` | 박터뜨리기 종료 후 호스트가 파티를 끝낼 수 있는 단계 |
| `END` | 호스트가 "종료"를 눌러 카운트다운 시작 |

---

## 2. Phase 전환 트리거

| 전환 | 트리거 | 구현 방식 |
|------|--------|-----------|
| `ENTRY → MUSIC` | 호스트 "다음" 버튼 | 새 `POST /phase/advance` (호스트 only) |
| `MUSIC → CANDLE` | 음악 종료 감지 | 동일 엔드포인트 (모든 참여자 가능 — CAS로 중복 방지) |
| `CANDLE → BURST` | 임의 참여자 "다음" 버튼 | 기존 `StartBurstGameUseCase`에 훅 |
| `BURST → CLOSEABLE` | 박터뜨리기 종료 | 기존 `HandleBurstGameEndedUseCase`에 훅 |
| `CLOSEABLE → END` | 호스트 "종료" 버튼 | 기존 `StartRealtimePartyEndUseCase`에 훅 |

**ENTRY→MUSIC 호스트 전용 이유:** 방향을 정하는 것은 호스트 역할이며, 다중 동시 호출이 발생하면 안 된다.

**MUSIC→CANDLE 모든 참여자 허용 이유:** 음악은 모두에게 동시에 종료된다. 누가 먼저 감지해도 CAS(Compare-And-Swap)로 첫 번째 호출만 실제 전환이 일어나고 나머지는 현재 phase를 그대로 반환한다.

---

## 3. 동기화 방식: 즉시(Immediate)

Phase 전환 시 서버는 즉시 `phaseStartedAt = now`로 기록하고 SSE 브로드캐스트한다.

```
호스트 "다음" 클릭
       │
       ▼
POST /phase/advance
       │
       ├─ PhaseStore: ENTRY → MUSIC, startedAt = 20:00:40
       └─ SSE 브로드캐스트 → party-phase-changed
                    │
                    ├─► 참여자 A: elapsed=0s, 음악 재생 시작
                    ├─► 참여자 B: elapsed=0s, 음악 재생 시작
                    └─► 참여자 C: elapsed=0s, 음악 재생 시작
```

수십 ms 오차는 생일 파티 앱에서 체감 불가 수준이므로 예약 시작(scheduled start) 방식은 채택하지 않는다.

**중간 입장자 타이머 맞추기:**

```
GET /phase 응답:
{
  "phaseStartedAt": "20:00:40",
  "serverNow":      "20:00:52"   ← elapsed = 12초
}
→ 클라이언트: 음악 12초 지점부터 재생
```

---

## 4. 저장 방식: 인메모리 (ConcurrentHashMap + Striped Lock)

`InMemoryBurstGameSessionStore` 패턴을 동일하게 적용한다.

- `ConcurrentHashMap<Long, PhaseEntry>` — key: partyId
- Striped lock (64개 슬롯) — partyId 기반 해시
- `null` = ENTRY (별도 초기화 불필요)
- 파티 종료 시 `removeByPartyId`로 정리

```kotlin
data class PhaseEntry(
    val phase: PartyPhase,
    val startedAt: LocalDateTime,
)
```

---

## 5. API

### 5-1. Phase 조회

```
GET /api/v1/parties/{partyId}/phase
```

- 인증: `X-Participant-Token` 또는 로그인 사용자
- 파티 멤버 여부 검증

응답:

```json
{
  "partyId": 1,
  "phase": "CANDLE",
  "phaseStartedAt": "2026-05-26T20:00:35",
  "serverNow": "2026-05-26T20:00:47"
}
```

### 5-2. Phase 전환

```
POST /api/v1/parties/{partyId}/phase/advance
```

요청:

```json
{ "currentPhase": "ENTRY" }
```

- `currentPhase`는 CAS 역할: 스토어의 현재 phase와 다르면 전환 없이 현재 phase 반환
- `ENTRY → MUSIC`: 호스트 only (`userId == party.ownerId`)
- `MUSIC → CANDLE`: 모든 파티 멤버 허용
- 그 외 phase는 이 엔드포인트로 전환 불가 (400 반환)

응답: GET과 동일 구조

---

## 6. SSE 이벤트

`RealtimePartyEventBroadcaster`에 `broadcastPhaseChanged` 추가.

```
event: party-phase-changed
data: {
  "partyId": 1,
  "phase": "MUSIC",
  "phaseStartedAt": "2026-05-26T20:00:40",
  "serverNow": "2026-05-26T20:00:40"
}
```

모든 참여자(호스트 포함)에게 broadcast.

---

## 7. 패키지 구조

새 파일:

```
party/
├── api/
│   ├── PartyPhaseApi.kt
│   └── PartyPhaseController.kt
├── application/
│   ├── port/
│   │   └── PartyPhaseStore.kt
│   ├── dto/
│   │   └── PartyPhaseResult.kt
│   └── usecase/
│       ├── GetPartyPhaseUseCase.kt
│       └── AdvancePartyPhaseUseCase.kt
├── domain/vo/
│   └── PartyPhase.kt
└── infrastructure/memory/
    └── InMemoryPartyPhaseStore.kt
```

수정 파일:

| 파일 | 변경 내용 |
|------|-----------|
| `RealtimePartyEventBroadcaster` | `broadcastPhaseChanged` 메서드 추가 |
| `ChatRealtimePartyEventBroadcaster` | `broadcastPhaseChanged` 구현 |
| `StartBurstGameUseCase` | CANDLE → BURST 전환 + 브로드캐스트 훅 |
| `HandleBurstGameEndedUseCase` | BURST → CLOSEABLE 전환 + 브로드캐스트 훅 |
| `StartRealtimePartyEndUseCase` | CLOSEABLE → END 전환 + 브로드캐스트 훅 |

---

## 8. 에러 처리

| 상황 | 응답 |
|------|------|
| 파티 미존재 | `404 RESOURCE_NOT_FOUND` |
| 파티 멤버 아닌 사용자의 조회 | `403 FORBIDDEN` |
| 호스트가 아닌 사용자의 ENTRY→MUSIC 전환 | `403 FORBIDDEN` |
| 허용되지 않는 `currentPhase` 값 | `400 BAD_REQUEST` |
| CAS 불일치 (이미 다른 phase) | `200 OK` — 현재 phase 그대로 반환 |

---

## 9. 테스트 시나리오

- ENTRY 상태에서 GET → `{ phase: "ENTRY", elapsed ≈ 0 }`
- 호스트가 ENTRY→MUSIC 전환 → SSE `party-phase-changed` 수신 확인
- 비호스트가 ENTRY→MUSIC 시도 → 403
- MUSIC→CANDLE 동시 호출 2개 → 첫 번째만 전환, 두 번째는 현재 phase 반환
- 중간 입장 후 GET → `phaseStartedAt` + `serverNow`로 경과 시간 계산 가능
- 파티 종료 후 phase 스토어에서 제거 확인
