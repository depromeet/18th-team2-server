# SSE 부하테스트 및 WebSocket 전환 설계

- 작성일: 2026-08-18
- 기준 브랜치: `feature/realtime-websocket-loadtest` (신규, `fix/realtime-party-bugs` 기준으로 분기 — develop 로컬 객체 손상으로 복구 후 재분기 가능)
- 운영 전제: 단일 애플리케이션 인스턴스, 로컬 1대 머신에서 클라이언트(k6)와 서버가 자원을 공유하는 조건

## 1. 목적

실시간 파티(`chat` 도메인)의 현재 통신 방식인 SSE의 동시접속 한계와 응답시간을 실측하고, 이를 근거로 WebSocket 전환 여부를 판단한다. 전환 시 SSE와 동일 조건으로 부하테스트를 재실행해 지표를 비교한다.

이 문서가 다루지 않는 것: 실제 운영 배포, 수평 확장(멀티 인스턴스) 검증, 미니게임(burst-game, candle-blow) 이벤트의 WebSocket 이전.

## 2. 사전 조사 결과 요약

- SSE는 `SseEmitterRegistry`(`chat/infrastructure/sse/`)가 파티ID → emitter 목록을 인메모리 `ConcurrentHashMap`으로 관리. Redis 등 외부 pub/sub 없음, 단일 JVM 전제.
- 별도 heartbeat/keep-alive 없음. emitter 타임아웃은 파티 진행시간 기반 동적 계산(약 962초).
- Tomcat 스레드풀/커넥션 설정은 전부 기본값(threads.max=200, max-connections=8192, accept-count=100) — 튜닝 안 됨.
- SSE 구독 엔드포인트(`POST /api/v1/party-invites/{inviteToken}/realtime-participants/stream`)는 `permitAll` — 비로그인 게스트도 접속 가능, `characterId`만 유효하면 됨.
- **`RealtimeParty.MAX_PARTICIPANTS = 14`는 서버에서 강제되지 않는 dead constant** — `GetPartyParticipantsUseCase`의 표시용(`"x/14"`)으로만 쓰이고 입장 로직(`RealtimePartyEntryProfileResolver`)에는 인원수 체크가 없음. 부하테스트에서 정원 초과로 막힐 걱정은 없지만, 이는 별도의 실제 버그이므로 이번 스코프에서는 코드 수정 없이 기록만 남긴다.
- 파티 생성(`POST /api/v1/parties/realtime`)과 초대링크 발급(`POST /api/v1/parties/{id}/invite-link`)은 JWT 필요, `/api/dev/token`으로 개발용 토큰 발급 가능(`prod` 프로파일에서는 비활성).
- `GET /api/v1/characters`는 인증 불필요, Flyway 시드로 `characterId 1~5`가 항상 존재.

## 3. 부하테스트 도구

기본 k6(v1.4.0)는 SSE 스트림(응답이 끝나지 않는 연결)을 다루지 못한다. `xk6-sse`(phymbert/xk6-sse) 확장을 얹은 커스텀 바이너리를 로컬에 빌드해 사용한다.

```
brew install go
go install go.k6.io/xk6/cmd/xk6@latest
xk6 build v1.4.0 --with github.com/phymbert/xk6-sse@latest --output ./k6-sse
```

- `k6-sse` 빌드 시 k6 버전을 `v1.4.0`으로 고정해야 한다. `latest`(v2.x)로 빌드하면 `go.k6.io/k6` vs `go.k6.io/k6/v2` 모듈 경로 충돌로 확장이 조용히 비활성화된다(경고만 뜨고 런타임에 `unknown dependency` 에러). 로컬 SSE 테스트 서버로 실제 이벤트 수신을 검증 완료.
- 빌드된 바이너리(약 46MB)는 저장소에 커밋하지 않는다. `loadtest/README.md`에 빌드 명령만 남긴다.
- WebSocket(STOMP) 구간은 k6 코어 `k6/experimental/websockets` 모듈로 충분(별도 확장 불필요).

## 4. 픽스처 시딩

k6 `setup()` 단계에서 1회 수행, 이후 5단계 부하테스트 전체가 공유:

1. `POST /api/dev/token?email=loadtest@test.com` → JWT 발급 (없으면 유저 자동 생성)
2. `POST /api/v1/parties/realtime` 를 약 80회 호출해 REALTIME 파티 풀 생성. `startedDate`/`startTime`은 테스트 시작 시각 기준 1분 전으로 맞춰 `LIVE_OPEN` 윈도우(`startedAt` ~ `startedAt+10분`) 안에 들어오게 한다.
3. 각 파티마다 `POST /api/v1/parties/{partyId}/invite-link` 호출해 `inviteToken` 수집
4. `characterId=1` 고정 사용

파티 풀 크기(80개)는 최대 동시접속 목표(1000명) 기준 파티당 약 12~14명 분산을 가정한 값이며, 정원 제약이 없으므로 재사용 가능하다(단계마다 새로 만들 필요 없음).

## 5. SSE 부하테스트 시나리오

- 계단: 50 → 100 → 200 → 500 → 1000명, 단계별 독립 실행(각각 별도 k6 run, 결과 JSON 분리 저장)
- 각 VU: 파티 풀에서 라운드로빈으로 파티 선택 → SSE 연결 오픈 → `entered` 이벤트 수신 시각 기록 → 20초 유지 → 정상 종료(`DELETE .../realtime-participants`로 leave 호출 후 커넥션 close)
- 측정 지표(단계별):
  - 연결 성공률 (커넥션 거부/5xx/타임아웃 비율)
  - 응답시간 = 요청 시작 → `entered` 이벤트 수신까지 (avg/p50/p90/p95/p99)
  - 에러율 5% 초과 또는 p95 응답시간이 임계값(예: 3초)을 넘는 첫 단계를 "한계점"으로 판정
- 서버 프로세스의 CPU/메모리는 `ps`/Activity Monitor 또는 JVM 자체 로그 수준에서 보조로만 관찰(별도 모니터링 스택 구축은 범위 밖)

## 6. WebSocket 최소 구현

- 의존성 추가: `spring-boot-starter-websocket` (`build.gradle.kts`)
- STOMP + 인메모리 심플 브로커(`enableSimpleBroker("/topic")`) — SSE와 동일하게 단일 JVM/외부 브로커 없음 조건을 맞춰 공정 비교
- 레이어드 아키텍처 규칙 준수:
  - `EnterRealtimePartyUseCase`, `EnterAndSubscribeChatUseCase`, `SendChatMessageUseCase`는 변경하지 않는다.
  - `chat/infrastructure/websocket/`에 신규 어댑터 추가. 기존 `PartySseEventPublisher` 포트를 그대로 구현(또는 SSE/WS 공통으로 쓸 수 있게 포트명을 일반화 검토)하여 `SimpMessagingTemplate`으로 `/topic/parties/{partyId}`에 전송.
  - STOMP CONNECT/SUBSCRIBE 시점에 `inviteToken`을 받아 기존 `EnterRealtimePartyUseCase.enter()`를 그대로 호출하는 `@MessageMapping` 핸들러를 `chat/api`(또는 대응 위치)에 추가.
- 미니게임 브로드캐스터(`SseBurstGameEventBroadcaster`, `SseCandleBlowEventBroadcaster`)는 이번 범위에서 WebSocket 대응 구현을 만들지 않는다(포트가 공유되면 자동으로 태워 보내지는지 여부는 부작용으로만 확인, 별도 테스트 안 함).

## 7. WebSocket 부하테스트

SSE와 동일한 파티 풀/계단/유지시간/leave 흐름을 k6 `k6/experimental/websockets`로 재구현. 측정 지표는 5절과 동일한 정의(응답시간 = CONNECT → `entered` 프레임 수신까지)를 사용해 직접 비교 가능하게 한다.

## 8. 산출물

- `loadtest/sse-loadtest.js`, `loadtest/ws-loadtest.js`, `loadtest/README.md` (커밋 대상, `loadtest/results/*.json`은 `.gitignore` 추가)
- 그라파나 스타일 HTML 비교 대시보드(Artifact) — 단계별 응답시간/에러율 라인차트, SSE vs WS 한계점 비교, `dataviz` 스킬 가이드 적용
- 트레이드오프/전환 체크리스트 문서 (본 대화 내 텍스트 또는 별도 산출물로 정리, 확장성·재연결·인증모델·메시지 순서보장·로드밸런서 sticky session 등 포함)

## 9. 결과 해석 시 명시할 제약

로컬 1대 머신에서 부하 클라이언트와 서버가 자원을 공유하므로 절대적인 "최대 동시접속 처리량" 수치는 실제 운영 서버 스펙에 그대로 대입할 수 없다. 이번 측정의 핵심 가치는 **동일 하드웨어·동일 조건에서의 SSE 대비 WebSocket 상대 비교**이며, 최종 리포트에 이 제약을 명시한다.
