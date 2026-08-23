# SSE 부하테스트 및 WebSocket 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티(SSE) 엔드포인트의 동시접속 한계·응답시간을 k6로 실측하고, 동일 조건으로 WebSocket(STOMP) 최소 구현을 만들어 부하테스트를 재실행해 두 지표를 비교하는 산출물(그래프+트레이드오프 문서)을 만든다.

**Architecture:** SSE는 기존 코드를 손대지 않고 순수 측정만 한다. WebSocket은 `EnterRealtimePartyUseCase.enter()`(기존, 미변경)를 그대로 재사용하는 새 UseCase/Controller/Gateway를 `chat` 도메인에 추가하고, SSE 전용으로 중복돼 있던 "채팅 히스토리 스냅샷 조회" 로직만 공용 지원 컴포넌트로 추출한다. 두 전송 방식 모두 k6로 "연결 시작 → 개인 entered 응답 수신"을 응답시간으로, 단계별(50/100/200/500/1000명) 동시접속에서의 에러율을 한계 판정 기준으로 측정한다.

**Tech Stack:** Kotlin/Spring Boot(WebMVC, STOMP `spring-boot-starter-websocket`), k6 v1.4.0(brew, WebSocket용) + xk6-sse 커스텀 빌드(SSE용, `k6-sse` 바이너리, 이미 `scratchpad/k6-sse`에 빌드됨), MySQL(Testcontainers/Docker Compose).

## Global Constraints

- 커밋 메시지: `<type>: <한국어 설명>`, scope 없음, 한국어 명사형 종결, 50자 이내, 마침표 없음 (`.claude/rules/git.md`)
- `git add -A`/`git add .` 금지, 파일 개별 지정
- `--no-verify` 금지
- 레이어드 아키텍처 규칙(`.claude/rules/layered-architecture.md`) 준수: UseCase는 1 public 메서드, `@Transactional`은 UseCase에만, Service는 `@Transactional` 금지, Repository 쓰기는 Service 경유
- 테스트는 `docs/testing-rules.md` 규칙 준수: `@SpringBootTest`는 `TestcontainersConfiguration` 경유, `jdbc:tc:` 금지
- 기준 브랜치: `feature/realtime-websocket-loadtest` (이미 checkout됨, `develop` 기반)
- 로컬 서버 포트: `8080`, MySQL: `docker/docker-compose.local.yml` (`localhost:3306`)
- `RealtimeParty` 상수(현재 `develop` 기준): `ENTERABLE_BEFORE_MINUTES=5`, `START_GRACE_MINUTES=30`, `LIVE_DURATION_MINUTES=10`, `LIVE_END_COUNTDOWN_SECONDS=60`, `MAX_PARTICIPANTS=14`(서버에서 강제되지 않는 표시 전용 상수 — 코드 수정하지 않음)
- k6 SSE 확장 빌드 바이너리는 저장소에 커밋하지 않는다 (46MB, `.gitignore` 처리)

---

## Task 1: loadtest 디렉터리 스캐폴딩

**Files:**
- Create: `loadtest/README.md`
- Create: `loadtest/results/.gitkeep`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `loadtest/` 디렉터리 구조 — 이후 모든 태스크가 이 아래에 파일을 추가한다. `loadtest/results/*.json`은 커밋 대상에서 제외된다.

- [ ] **Step 1: `.gitignore`에 결과물/바이너리 제외 규칙 추가**

기존 `.gitignore` 파일 끝에 다음을 추가한다 (파일 상단을 확인해 중복 섹션 없이 append):

```gitignore

# 부하테스트 산출물 (loadtest)
loadtest/results/*.json
loadtest/k6-sse
loadtest/bin/
```

- [ ] **Step 2: `loadtest/results/.gitkeep` 생성**

빈 파일로 생성한다 (results 디렉터리를 git에 존재시키기 위함, 내용물 자체는 gitignore로 제외됨).

- [ ] **Step 3: `loadtest/README.md` 작성**

```markdown
# 실시간 파티 부하테스트 (SSE vs WebSocket)

로컬 1대 머신에서 k6 부하 클라이언트와 Spring 서버가 자원을 공유하는 조건으로
SSE와 WebSocket(STOMP) 각각의 동시접속 한계·응답시간을 측정하고 비교한다.

## 사전 준비

1. MySQL 기동: `docker compose -f docker/docker-compose.local.yml up -d`
2. 서버 기동: `./gradlew bootRun` (기본 `local` 프로파일, `localhost:8080`)
3. SSE 부하테스트용 k6 바이너리 빌드 (WebSocket은 기본 k6로 충분, 별도 빌드 불필요):

   ```bash
   brew install go
   go install go.k6.io/xk6/cmd/xk6@latest
   ~/go/bin/xk6 build v1.4.0 --with github.com/phymbert/xk6-sse@latest --output loadtest/k6-sse
   ```

   **주의**: `xk6 build`에 버전을 `v1.4.0`으로 반드시 고정한다. `latest`(v2.x)로 빌드하면
   `go.k6.io/k6` vs `go.k6.io/k6/v2` 모듈 경로 충돌로 SSE 확장이 조용히 비활성화되고
   런타임에 `unknown dependency` 에러가 난다.

## 실행

```bash
# SSE, 계단식 50/100/200/500/1000명
./loadtest/run-stage.sh sse-loadtest.js ./loadtest/k6-sse

# WebSocket, 계단식 50/100/200/500/1000명 (기본 brew k6 사용)
./loadtest/run-stage.sh ws-loadtest.js k6
```

결과는 `loadtest/results/{sse,ws}-loadtest-{VUS}.json`에 저장된다 (git 추적 안 함).

## 측정 정의

- **응답시간**: 연결 시작 → 본인의 `entered` 이벤트(개인 입장 확인) 수신까지
- **동시접속 단계**: 50 / 100 / 200 / 500 / 1000, 각 단계 20초 연결 유지 후 정상 종료
- **한계점**: 에러율 5% 초과 또는 p95 응답시간이 3초를 넘는 첫 단계
```

- [ ] **Step 4: Commit**

```bash
git add loadtest/README.md loadtest/results/.gitkeep .gitignore
git commit -m "$(cat <<'EOF'
chore: 부하테스트 디렉터리 스캐폴딩 추가
EOF
)"
```

---

## Task 2: 로컬 앱 기동 확인

**Files:** 없음 (코드 변경 없음, 운영 확인 태스크)

**Interfaces:**
- Consumes: 없음
- Produces: `http://localhost:8080` 에서 응답하는 로컬 서버 — 이후 모든 실행 태스크가 이 서버를 대상으로 한다.

- [ ] **Step 1: MySQL 기동**

```bash
docker compose -f docker/docker-compose.local.yml up -d
```

- [ ] **Step 2: 서버 기동 (백그라운드)**

```bash
./gradlew bootRun > /tmp/team2-server.log 2>&1 &
```

- [ ] **Step 3: 헬스체크로 기동 확인**

```bash
until curl -sf http://localhost:8080/actuator/health > /dev/null; do sleep 2; done
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP", ...}` 출력. 실패 시 `/tmp/team2-server.log` 확인.

- [ ] **Step 4: 캐릭터 시드 데이터 확인**

```bash
curl -s http://localhost:8080/api/v1/characters | head -c 300
```

Expected: `characterId: 1` 포함된 JSON 배열 (Flyway 시드 데이터).

---

## Task 3: 공용 픽스처 시딩 모듈 작성 (`loadtest/lib/fixtures.js`)

**Files:**
- Create: `loadtest/lib/fixtures.js`

**Interfaces:**
- Consumes: 로컬 서버 (`Task 2`에서 기동)
- Produces: `seedFixtures(baseUrl, partyCount)` — `{ invites: [{ partyId, token }], characterId }`를 반환. `sse-loadtest.js`(Task 5), `ws-loadtest.js`(Task 11)의 `setup()`에서 사용.

- [ ] **Step 1: `loadtest/lib/fixtures.js` 작성**

```javascript
import http from 'k6/http';
import { check } from 'k6';

const DEV_EMAIL = 'loadtest@team2.local';

export function seedFixtures(baseUrl, partyCount) {
  const tokenRes = http.post(`${baseUrl}/api/dev/token?email=${DEV_EMAIL}`);
  check(tokenRes, { 'dev token issued': (r) => r.status === 200 });
  if (tokenRes.status !== 200) {
    throw new Error(`dev token issue failed: ${tokenRes.status} ${tokenRes.body}`);
  }
  const jwt = tokenRes.json('data').token;
  const authHeaders = {
    headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
  };

  const now = new Date(Date.now() - 60 * 1000); // 1분 전 시작 -> LIVE_OPEN 보장
  const startedDate = now.toISOString().slice(0, 10);
  const startTime = now.toISOString().slice(11, 16);

  const invites = [];
  for (let i = 0; i < partyCount; i++) {
    const createRes = http.post(
      `${baseUrl}/api/v1/parties/realtime`,
      JSON.stringify({
        celebrantNickname: `LoadTestHost${i}`,
        startedDate,
        startTime,
        characterId: 1,
      }),
      authHeaders,
    );
    if (createRes.status !== 201) {
      throw new Error(`party create failed [${i}]: ${createRes.status} ${createRes.body}`);
    }
    const partyId = createRes.json('data').partyId;

    const inviteRes = http.post(
      `${baseUrl}/api/v1/parties/${partyId}/invite-link`,
      null,
      authHeaders,
    );
    if (inviteRes.status !== 200) {
      throw new Error(`invite create failed [${i}]: ${inviteRes.status} ${inviteRes.body}`);
    }
    invites.push({ partyId, token: inviteRes.json('data').token });
  }

  return { invites, characterId: 1 };
}
```

- [ ] **Step 2: 단독 실행으로 시딩 검증 (Task 2의 로컬 서버 대상)**

```bash
cat > /tmp/fixtures_smoke.js <<'EOF'
import { seedFixtures } from '/Users/heoeunjeong/Desktop/18th-team2-server/loadtest/lib/fixtures.js';

export function setup() {
  const data = seedFixtures('http://localhost:8080', 3);
  console.log('invites:', JSON.stringify(data.invites));
  return data;
}

export default function (data) {}
EOF
k6 run --vus 1 --iterations 1 /tmp/fixtures_smoke.js
rm /tmp/fixtures_smoke.js
```

Expected: 콘솔에 `invites:` 로그로 `partyId`, `token` 3쌍이 출력되고 에러 없이 종료(exit code 0).

- [ ] **Step 3: Commit**

```bash
git add loadtest/lib/fixtures.js
git commit -m "$(cat <<'EOF'
test: 부하테스트 파티 픽스처 시딩 모듈 추가
EOF
)"
```

---

## Task 4: STOMP 프레임 인코딩/디코딩 헬퍼 작성 (`loadtest/lib/stomp.js`)

**Files:**
- Create: `loadtest/lib/stomp.js`

**Interfaces:**
- Produces: `encodeFrame(command, headers, body)` → STOMP 텍스트 프레임 문자열, `parseFrames(raw)` → `{command, headers, body}[]`. Task 11(`ws-loadtest.js`)에서 사용.
- 알려진 단순화: 하나의 WebSocket 텍스트 메시지에 하나의 STOMP 프레임만 담기는 경우를 가정한다(이번 부하테스트의 소규모 JSON payload에서는 항상 성립). 프레임이 여러 WS 메시지에 걸쳐 분할되는 경우는 다루지 않는다.

- [ ] **Step 1: `loadtest/lib/stomp.js` 작성**

```javascript
export function encodeFrame(command, headers, body) {
  let frame = command + '\n';
  for (const key in headers) {
    frame += `${key}:${headers[key]}\n`;
  }
  frame += '\n';
  frame += body || '';
  frame += ' ';
  return frame;
}

export function parseFrames(raw) {
  return raw
    .split(' ')
    .map((chunk) => chunk.replace(/^\n+/, ''))
    .filter((chunk) => chunk.trim().length > 0)
    .map((chunk) => {
      const lines = chunk.split('\n');
      const command = lines[0];
      const headers = {};
      let i = 1;
      for (; i < lines.length; i++) {
        if (lines[i] === '') {
          i++;
          break;
        }
        const idx = lines[i].indexOf(':');
        headers[lines[i].slice(0, idx)] = lines[i].slice(idx + 1);
      }
      const body = lines.slice(i).join('\n');
      return { command, headers, body };
    });
}
```

- [ ] **Step 2: 단위 검증 스크립트로 왕복(encode→parse) 확인**

```bash
cat > /tmp/stomp_smoke.js <<'EOF'
import { encodeFrame, parseFrames } from '/Users/heoeunjeong/Desktop/18th-team2-server/loadtest/lib/stomp.js';

export default function () {
  const raw = encodeFrame('SEND', { destination: '/app/x', 'content-type': 'application/json' }, '{"a":1}');
  const frames = parseFrames(raw);
  if (frames.length !== 1) throw new Error(`expected 1 frame, got ${frames.length}`);
  if (frames[0].command !== 'SEND') throw new Error(`command mismatch: ${frames[0].command}`);
  if (frames[0].headers.destination !== '/app/x') throw new Error('destination header mismatch');
  if (frames[0].body !== '{"a":1}') throw new Error(`body mismatch: ${frames[0].body}`);
  console.log('stomp roundtrip OK');
}
EOF
k6 run --vus 1 --iterations 1 /tmp/stomp_smoke.js
rm /tmp/stomp_smoke.js
```

Expected: `stomp roundtrip OK` 로그, exit code 0.

- [ ] **Step 3: Commit**

```bash
git add loadtest/lib/stomp.js
git commit -m "$(cat <<'EOF'
test: STOMP 프레임 인코딩 헬퍼 추가
EOF
)"
```

---

## Task 5: SSE 부하테스트 스크립트 작성 (`loadtest/sse-loadtest.js`)

**Files:**
- Create: `loadtest/sse-loadtest.js`

**Interfaces:**
- Consumes: `seedFixtures` (Task 3), `k6/x/sse` (커스텀 빌드 `loadtest/k6-sse`, Task 1 README 안내대로 로컬에서 빌드된 상태 가정)
- Produces: 실행 시 `sse_entered_response_time`(Trend), `sse_connect_success`(Rate) 커스텀 메트릭. `--summary-export`로 JSON 저장(Task 6에서 사용).

- [ ] **Step 1: `loadtest/sse-loadtest.js` 작성**

```javascript
import sse from 'k6/x/sse';
import { sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { seedFixtures } from './lib/fixtures.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PARTY_COUNT = parseInt(__ENV.PARTY_COUNT || '80', 10);
const HOLD_SECONDS = parseInt(__ENV.HOLD_SECONDS || '20', 10);
const VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
  scenarios: {
    stage: {
      executor: 'constant-vus',
      vus: VUS,
      duration: `${HOLD_SECONDS + 15}s`,
    },
  },
  setupTimeout: '180s',
};

const enteredResponseTime = new Trend('sse_entered_response_time', true);
const connectSuccess = new Rate('sse_connect_success');

export function setup() {
  return seedFixtures(BASE_URL, PARTY_COUNT);
}

export default function (data) {
  const invite = data.invites[__VU % data.invites.length];
  const url = `${BASE_URL}/api/v1/party-invites/${invite.token}/realtime-participants/stream`;
  const params = {
    method: 'POST',
    body: JSON.stringify({ nickname: `u${__VU}-${__ITER}`, characterId: data.characterId }),
    headers: { 'Content-Type': 'application/json' },
  };
  const startedAt = Date.now();
  let entered = false;

  sse.open(url, params, function (client) {
    client.on('event', function (event) {
      if (event.name === 'entered' && !entered) {
        entered = true;
        connectSuccess.add(true);
        enteredResponseTime.add(Date.now() - startedAt);
        sleep(HOLD_SECONDS);
        client.close();
      }
    });
    client.on('error', function () {
      if (!entered) connectSuccess.add(false);
    });
  });
}
```

- [ ] **Step 2: 소규모 스모크(vus=2)로 동작 확인** (`loadtest/k6-sse` 바이너리 필요, Task 1 README 참고)

```bash
cd /Users/heoeunjeong/Desktop/18th-team2-server
VUS=2 HOLD_SECONDS=3 ./loadtest/k6-sse run loadtest/sse-loadtest.js 2>&1 | tail -30
```

Expected: `sse_connect_success` 100%, `sse_entered_response_time` 값 존재, 에러 없이 종료.

- [ ] **Step 3: Commit**

```bash
git add loadtest/sse-loadtest.js
git commit -m "$(cat <<'EOF'
test: SSE 부하테스트 스크립트 추가
EOF
)"
```

---

## Task 6: SSE 계단식 부하테스트 실행 및 결과 저장

**Files:**
- Create: `loadtest/run-stage.sh`

**Interfaces:**
- Consumes: `loadtest/sse-loadtest.js`(Task 5), `loadtest/ws-loadtest.js`(Task 11, 이 태스크 실행 시점엔 아직 없음 — SSE만 먼저 실행)
- Produces: `loadtest/results/sse-loadtest-{50,100,200,500,1000}.json`

- [ ] **Step 1: `loadtest/run-stage.sh` 작성**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT=$1   # 예: sse-loadtest.js
BIN=$2      # 예: ./loadtest/k6-sse 또는 k6

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$ROOT_DIR/results"

NAME=$(basename "$SCRIPT" .js)

for VUS in 50 100 200 500 1000; do
  echo "=== VUS=$VUS ($SCRIPT) ==="
  VUS=$VUS "$BIN" run \
    --summary-export="$ROOT_DIR/results/${NAME}-${VUS}.json" \
    "$ROOT_DIR/$SCRIPT"
done
```

- [ ] **Step 2: 실행 권한 부여**

```bash
chmod +x loadtest/run-stage.sh
```

- [ ] **Step 3: SSE 계단식 실행** (Task 2 서버가 기동 중이어야 함)

```bash
./loadtest/run-stage.sh sse-loadtest.js ./loadtest/k6-sse
```

Expected: 5단계(50/100/200/500/1000) 모두 완료, `loadtest/results/sse-loadtest-{50,100,200,500,1000}.json` 5개 파일 생성. 특정 단계에서 에러율이 급증하면 그 단계 로그에 `sse_connect_success` 하락이 보인다 — 실패해도 스크립트는 계속 다음 단계로 진행되므로 전체 완료까지 기다린다.

- [ ] **Step 4: 결과 파일 존재 확인**

```bash
ls -la loadtest/results/sse-loadtest-*.json
```

Expected: 5개 JSON 파일.

- [ ] **Step 5: Commit** (스크립트만 커밋, 결과 JSON은 `.gitignore` 대상)

```bash
git add loadtest/run-stage.sh
git commit -m "$(cat <<'EOF'
test: 계단식 부하테스트 실행 스크립트 추가
EOF
)"
```

---

## Task 7: WebSocket 의존성 및 기본 설정 추가

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/team2/server/chat/infrastructure/websocket/WebSocketConfig.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`

**Interfaces:**
- Produces: STOMP 엔드포인트 `/ws`(SockJS 없이 raw WebSocket), 브로커 prefix `/topic`, 앱 prefix `/app`. Task 9의 `ChatSocketController`, `ChatSocketGateway`가 이 설정에 의존한다.

- [ ] **Step 1: `build.gradle.kts`에 WebSocket 스타터 추가**

`implementation("org.springframework.boot:spring-boot-starter-webmvc")` 줄 바로 아래에 추가:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-websocket")
```

- [ ] **Step 2: `WebSocketConfig.kt` 작성**

```kotlin
package com.team2.server.chat.infrastructure.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
    }
}
```

- [ ] **Step 3: `SecurityConfig.kt`에 WebSocket 핸드셰이크 엔드포인트 permit 추가**

`auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/phase/advance").permitAll()` 줄 바로 아래에 추가:

```kotlin
                auth.requestMatchers("/ws/**").permitAll()
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/main/kotlin/com/team2/server/chat/infrastructure/websocket/WebSocketConfig.kt src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
git commit -m "$(cat <<'EOF'
feat: WebSocket STOMP 기본 설정 추가
EOF
)"
```

---

## Task 8: 채팅 히스토리 스냅샷 조회 로직 공용화

기존 `EnterAndSubscribeChatUseCase`에 SSE 전용으로 인라인돼 있던 "채팅 메시지 히스토리 + 캐릭터 썸네일 URL 조회" 로직을 별도 지원 컴포넌트로 추출한다. WebSocket용 UseCase(Task 9)가 동일 로직을 중복 없이 재사용하기 위함이다. `EnterRealtimePartyUseCase.enter()` 자체는 건드리지 않는다.

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/application/support/ChatHistorySnapshotResolver.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt`
- Modify: `src/test/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCaseTest.kt`

**Interfaces:**
- Produces: `ChatHistorySnapshotResolver.resolve(partyId: Long, enteringCharacterId: Long?): ChatHistorySnapshotResolver.Snapshot` where `Snapshot(messages: List<ChatMessageResponse>, enteringCharacterImageUrl: String?)`. Task 9의 `EnterAndSubscribeChatSocketUseCase`가 이 타입을 그대로 사용한다.

- [ ] **Step 1: 기존 테스트 통과 확인 (리팩터 전 baseline)**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.EnterAndSubscribeChatUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`, 3개 테스트 모두 PASS.

- [ ] **Step 2: `ChatHistorySnapshotResolver.kt` 작성**

```kotlin
package com.team2.server.chat.application.support

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import org.springframework.stereotype.Component

@Component
class ChatHistorySnapshotResolver(
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    data class Snapshot(
        val messages: List<ChatMessageResponse>,
        val enteringCharacterImageUrl: String?,
    )

    fun resolve(
        partyId: Long,
        enteringCharacterId: Long?,
    ): Snapshot {
        val rawMessages = chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(partyId)
        val characterIds =
            (rawMessages.mapNotNull { it.profile.character?.id } + enteringCharacterId)
                .filterNotNull()
                .distinct()
        val imageUrlMap =
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                characterIds,
                CHARACTER_THUMBNAIL_SORT_ORDER,
            )
        val messages =
            rawMessages.map {
                ChatMessageResponse.from(
                    message = it,
                    isCelebrant = it.profile.participant.isCelebrant,
                    imageUrl = it.profile.character?.let { c -> imageUrlMap[c.id] },
                )
            }
        return Snapshot(
            messages = messages,
            enteringCharacterImageUrl = enteringCharacterId?.let { imageUrlMap[it] },
        )
    }

    private companion object {
        const val CHARACTER_THUMBNAIL_SORT_ORDER = 1
    }
}
```

- [ ] **Step 3: `EnterAndSubscribeChatUseCase.kt` 전체 교체**

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.UserEnteredEventPayload
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class EnterAndSubscribeChatUseCase(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val chatHistorySnapshotResolver: ChatHistorySnapshotResolver,
    private val chatSseGateway: ChatSseGateway,
) {
    @Transactional
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): SseEmitter {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)
        val snapshot = chatHistorySnapshotResolver.resolve(enterResult.partyId, enterResult.characterId)

        val enteredPayload =
            UserEnteredEventPayload(
                nickname = enterResult.nickname,
                characterId = enterResult.characterId,
                characterImageUrl = snapshot.enteringCharacterImageUrl,
                role = if (enterResult.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )

        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)
        chatSseGateway.subscribe(enterResult.partyId, emitter, enterResult.participantToken)
        sendPartyState(emitter, enterResult.partyState)
        sendEntered(emitter, enterResult.participantToken, snapshot.messages)

        chatSseGateway.broadcastAfterCommit(
            enterResult.partyId,
            SseEmitter
                .event()
                .name("user-entered")
                .data(enteredPayload)
                .build(),
            excludeToken = enterResult.participantToken,
        )
        return emitter
    }

    private fun sendPartyState(
        emitter: SseEmitter,
        partyState: RealtimePartyStateResult,
    ) {
        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("party-state")
                    .data(partyState)
                    .build(),
            )
        } catch (e: IllegalStateException) {
            emitter.completeWithError(e)
        } catch (e: java.io.IOException) {
            emitter.completeWithError(e)
        }
    }

    private fun sendEntered(
        emitter: SseEmitter,
        participantToken: String,
        messages: List<ChatMessageResponse>,
    ) {
        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("entered")
                    .data(EnterRealtimePartyResponse(participantToken, messages))
                    .build(),
            )
        } catch (e: IllegalStateException) {
            emitter.completeWithError(e)
        } catch (e: java.io.IOException) {
            emitter.completeWithError(e)
        }
    }

    companion object {
        private const val SSE_GRACE_CLEANUP_SECONDS = 2L
        private const val EMITTER_TIMEOUT_MS =
            (
                (
                    RealtimeParty.ENTERABLE_BEFORE_MINUTES +
                        RealtimeParty.START_GRACE_MINUTES +
                        RealtimeParty.LIVE_DURATION_MINUTES
                ) * 60 +
                    RealtimeParty.LIVE_END_COUNTDOWN_SECONDS +
                    SSE_GRACE_CLEANUP_SECONDS
            ) * 1000L
    }
}
```

- [ ] **Step 4: `EnterAndSubscribeChatUseCaseTest.kt` 전체 교체** (mock 대상을 `chatMessageRepository`/`imageUrlReader`에서 `chatHistorySnapshotResolver` 하나로 축소)

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.application.dto.EnterRealtimePartyResult
import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterAndSubscribeChatUseCaseTest {
    @Mock lateinit var enterRealtimePartyUseCase: EnterRealtimePartyUseCase

    @Mock lateinit var chatHistorySnapshotResolver: ChatHistorySnapshotResolver

    @Mock lateinit var chatSseGateway: ChatSseGateway

    @InjectMocks
    lateinit var useCase: EnterAndSubscribeChatUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)

    private fun enterResult(
        partyId: Long = 1L,
        isCelebrant: Boolean = false,
    ): EnterRealtimePartyResult =
        EnterRealtimePartyResult(
            participantToken = "abc12345",
            partyId = partyId,
            startedAt = now.minusMinutes(5),
            isCelebrant = isCelebrant,
            nickname = "토끼왕",
            characterId = 1L,
            partyState =
                RealtimePartyStateResult(
                    partyId = partyId,
                    status = RealtimePartyStatus.LIVE_OPEN,
                    liveStartAt = now.minusMinutes(5),
                    endingStartedAt = null,
                    endedAt = now.plusMinutes(5).plusSeconds(60),
                    endingReason = null,
                    hostNickname = "주최자",
                    hostFarewellAvailable = true,
                    hostFarewellAvailableAt = now.minusMinutes(1),
                    serverNow = now,
                ),
        )

    @Test
    fun `입장 성공 - entered 이벤트와 함께 emitter 반환`() {
        val enterResult = enterResult()
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(ChatHistorySnapshotResolver.Snapshot(messages = emptyList(), enteringCharacterImageUrl = null))

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
    }

    @Test
    fun `히스토리 없어도 entered 이벤트 전송`() {
        val enterResult = enterResult()
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(ChatHistorySnapshotResolver.Snapshot(messages = emptyList(), enteringCharacterImageUrl = null))

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
    }

    @Test
    fun `입장 성공 - user-entered 이벤트 브로드캐스트`() {
        val enterResult = enterResult(isCelebrant = true)
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(
                ChatHistorySnapshotResolver.Snapshot(
                    messages = emptyList(),
                    enteringCharacterImageUrl = "https://example.com/rabbit.png",
                ),
            )

        useCase.enterAndSubscribe("tok", null, request)

        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
    }
}
```

- [ ] **Step 5: 리팩터 후 테스트 재실행**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.EnterAndSubscribeChatUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`, 3개 테스트 모두 PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/team2/server/chat/application/support/ChatHistorySnapshotResolver.kt src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCase.kt src/test/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatUseCaseTest.kt
git commit -m "$(cat <<'EOF'
refactor: 채팅 히스토리 스냅샷 조회 로직 공용 컴포넌트로 추출
EOF
)"
```

---

## Task 9: WebSocket 입장/구독/브로드캐스트 구현

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartySocketRequest.kt`
- Create: `src/main/kotlin/com/team2/server/chat/infrastructure/websocket/SocketBroadcastEvent.kt`
- Create: `src/main/kotlin/com/team2/server/chat/infrastructure/websocket/ChatSocketGateway.kt`
- Create: `src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatSocketUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/chat/controller/ChatSocketController.kt`

**Interfaces:**
- Consumes: `EnterRealtimePartyUseCase.enter(inviteToken, userId, request): EnterRealtimePartyResult` (기존, 미변경), `ChatHistorySnapshotResolver.resolve(partyId, characterId): Snapshot` (Task 8)
- Produces: STOMP 목적지 `/app/party-invites/{inviteToken}/realtime-participants` (SEND), `/topic/parties/{partyId}/personal/{clientRequestId}` (개인 응답), `/topic/parties/{partyId}` (브로드캐스트). Task 10 통합 테스트, Task 11 `ws-loadtest.js`가 이 프로토콜을 사용한다.
- **스코프**: 입장(enter)+구독(subscribe)+`user-entered` 브로드캐스트만 구현한다. 일반 채팅 메시지 전송(`SendChatMessageUseCase`)과 퇴장 브로드캐스트(`LeaveChatUseCase`)는 WebSocket 대응 구현을 만들지 않는다 — 이 두 UseCase는 SSE 전용 `ChatSseGateway`(concrete class)에 직접 의존하고 있어 손대지 않으면 WS로 자동 전달되지 않으며, 이번 부하테스트 비교 대상(연결+입장 응답시간)에도 해당하지 않는다.

- [ ] **Step 1: `EnterRealtimePartySocketRequest.kt` 작성**

```kotlin
package com.team2.server.chat.dto

data class EnterRealtimePartySocketRequest(
    val nickname: String,
    val characterId: Long,
    val participantToken: String? = null,
    val clientRequestId: String,
)
```

- [ ] **Step 2: `SocketBroadcastEvent.kt` 작성**

```kotlin
package com.team2.server.chat.infrastructure.websocket

data class SocketEventMessage(
    val event: String,
    val data: Any,
)

data class SocketBroadcastEvent(
    val partyId: Long,
    val eventName: String,
    val payload: Any,
)
```

- [ ] **Step 3: `ChatSocketGateway.kt` 작성**

```kotlin
package com.team2.server.chat.infrastructure.websocket

import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class ChatSocketGateway(
    private val messagingTemplate: SimpMessagingTemplate,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun sendPersonal(
        partyId: Long,
        clientRequestId: String,
        eventName: String,
        payload: Any,
    ) {
        messagingTemplate.convertAndSend(
            "/topic/parties/$partyId/personal/$clientRequestId",
            SocketEventMessage(eventName, payload),
        )
    }

    fun broadcastAfterCommit(
        partyId: Long,
        eventName: String,
        payload: Any,
    ) {
        val event = SocketBroadcastEvent(partyId, eventName, payload)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            applicationEventPublisher.publishEvent(event)
        } else {
            broadcast(event)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBroadcast(event: SocketBroadcastEvent) {
        broadcast(event)
    }

    private fun broadcast(event: SocketBroadcastEvent) {
        messagingTemplate.convertAndSend(
            "/topic/parties/${event.partyId}",
            SocketEventMessage(event.eventName, event.payload),
        )
    }
}
```

- [ ] **Step 4: `EnterAndSubscribeChatSocketUseCase.kt` 작성**

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.UserEnteredEventPayload
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EnterAndSubscribeChatSocketUseCase(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val chatHistorySnapshotResolver: ChatHistorySnapshotResolver,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @Transactional
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        clientRequestId: String,
    ) {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)
        val snapshot = chatHistorySnapshotResolver.resolve(enterResult.partyId, enterResult.characterId)

        chatSocketGateway.sendPersonal(enterResult.partyId, clientRequestId, "party-state", enterResult.partyState)
        chatSocketGateway.sendPersonal(
            enterResult.partyId,
            clientRequestId,
            "entered",
            EnterRealtimePartyResponse(enterResult.participantToken, snapshot.messages),
        )

        val enteredEventPayload =
            UserEnteredEventPayload(
                nickname = enterResult.nickname,
                characterId = enterResult.characterId,
                characterImageUrl = snapshot.enteringCharacterImageUrl,
                role = if (enterResult.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )
        chatSocketGateway.broadcastAfterCommit(enterResult.partyId, "user-entered", enteredEventPayload)
    }
}
```

- [ ] **Step 5: `ChatSocketController.kt` 작성**

```kotlin
package com.team2.server.chat.controller

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartySocketRequest
import com.team2.server.chat.usecase.EnterAndSubscribeChatSocketUseCase
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

@Controller
class ChatSocketController(
    private val enterAndSubscribeChatSocketUseCase: EnterAndSubscribeChatSocketUseCase,
) {
    @MessageMapping("/party-invites/{inviteToken}/realtime-participants")
    fun enterAndSubscribe(
        @DestinationVariable inviteToken: String,
        @Payload request: EnterRealtimePartySocketRequest,
    ) {
        enterAndSubscribeChatSocketUseCase.enterAndSubscribe(
            inviteToken = inviteToken,
            userId = null,
            request = EnterRealtimePartyRequest(request.nickname, request.characterId, request.participantToken),
            clientRequestId = request.clientRequestId,
        )
    }
}
```

- [ ] **Step 6: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartySocketRequest.kt src/main/kotlin/com/team2/server/chat/infrastructure/websocket/SocketBroadcastEvent.kt src/main/kotlin/com/team2/server/chat/infrastructure/websocket/ChatSocketGateway.kt src/main/kotlin/com/team2/server/chat/usecase/EnterAndSubscribeChatSocketUseCase.kt src/main/kotlin/com/team2/server/chat/controller/ChatSocketController.kt
git commit -m "$(cat <<'EOF'
feat: WebSocket 입장/구독/브로드캐스트 최소 구현 추가
EOF
)"
```

---

## Task 10: WebSocket 통합 테스트 작성

`docs/testing-rules.md`가 정의한 3개 캐싱 fingerprint(`@SpringBootTest + @Import(TC)`, `@SpringBootTest + @AutoConfigureMockMvc + @Import(TC)`, `@DataJpaTest + @Import(TC)`) 중 어느 것도 실제 소켓 업그레이드가 필요한 WebSocket 통합 테스트에는 맞지 않는다 (`MockMvc`는 실제 TCP 연결을 만들지 않아 STOMP 핸드셰이크를 검증할 수 없음). `webEnvironment = RANDOM_PORT`가 이 케이스에서는 불가피한 예외다.

**Files:**
- Create: `src/test/kotlin/com/team2/server/chat/controller/ChatSocketControllerTest.kt`

**Interfaces:**
- Consumes: `TestcontainersConfiguration`(MySQL), Task 9의 STOMP 프로토콜(`/ws`, `/app/party-invites/{token}/realtime-participants`, `/topic/parties/{partyId}/personal/{clientRequestId}`)

- [ ] **Step 1: 사전 확인 — 실시간 파티/초대 픽스처를 만드는 기존 테스트 헬퍼가 있는지 확인**

```bash
grep -rl "fun createRealtimeParty\|fun createInvite" src/test/kotlin/com/team2/server/chat src/test/kotlin/com/team2/server/party 2>/dev/null
```

이 grep 결과에 나온 기존 fixture 헬퍼가 있으면 재사용한다. 없으면 Step 2의 테스트에서 리포지토리로 직접 데이터를 만든다.

- [ ] **Step 2: `ChatSocketControllerTest.kt` 작성**

```kotlin
package com.team2.server.chat.controller

import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class ChatSocketControllerTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var partyInviteRepository: PartyInviteRepository

    private lateinit var stompClient: WebSocketStompClient

    @BeforeEach
    fun setUp() {
        // 서버가 registerStompEndpoints("/ws")를 SockJS 없이 등록하므로 순수 WebSocket 클라이언트를 사용한다.
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun tearDown() {
        stompClient.stop()
    }

    @Test
    fun `WebSocket으로 입장하면 개인 entered 응답과 브로드캐스트를 받는다`() {
        val now = LocalDateTime.now()
        val party =
            partyRepository.save(
                RealtimeParty(ownerId = 1L, celebrantNickname = "생일자", startedAt = now.minusMinutes(1)),
            )
        val invite =
            partyInviteRepository.save(
                // PartyInvite.token 컬럼은 length=16 — UUID 원본(36자)을 그대로 쓰면 DB 저장 시 길이 초과로 실패한다.
                PartyInvite(
                    party = party,
                    token = UUID.randomUUID().toString().replace("-", "").take(16),
                    expiresAt = now.plusHours(1),
                ),
            )

        val session =
            stompClient
                .connectAsync("ws://localhost:$port/ws", StompSessionHandlerAdapter())
                .get(5, TimeUnit.SECONDS)

        val clientRequestId = UUID.randomUUID().toString()
        val enteredFuture = CompletableFuture<Map<String, Any>>()

        session.subscribe(
            "/topic/parties/${party.id}/personal/$clientRequestId",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                @Suppress("UNCHECKED_CAST")
                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val body = payload as Map<String, Any>
                    if (body["event"] == "entered") {
                        enteredFuture.complete(body)
                    }
                }
            },
        )

        session.send(
            "/app/party-invites/${invite.token}/realtime-participants",
            mapOf(
                "nickname" to "테스트유저",
                "characterId" to 1,
                "clientRequestId" to clientRequestId,
            ),
        )

        val entered = enteredFuture.get(5, TimeUnit.SECONDS)
        assertEquals("entered", entered["event"])
        assertTrue((entered["data"] as Map<*, *>).containsKey("participantToken"))

        session.disconnect()
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.chat.controller.ChatSocketControllerTest"
```

Expected: `BUILD SUCCESSFUL`, 1개 테스트 PASS. 실패 시 `docker ps -a --filter "label=org.testcontainers"`로 컨테이너 상태 확인 후 재시도.

`RealtimeParty`, `PartyInvite`, `PartyRepository`, `PartyInviteRepository`의 생성자/시그니처는 이 플랜 작성 시점 기준(`feature/realtime-websocket-loadtest`, `develop` 최신)으로 직접 확인해 위 코드에 반영했다. 다만 실행 시점에 `develop`이 추가로 변경됐을 수 있으므로, 컴파일 에러가 나면 해당 파일을 다시 읽어 시그니처 차이만 맞춘다.

- [ ] **Step 4: 컨테이너 누수 확인**

```bash
docker ps -a --filter "label=org.testcontainers"
```

Expected: 이 테스트로 인한 잔존 컨테이너 없음 (0개이거나 다른 테스트의 공유 컨테이너만 존재).

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/team2/server/chat/controller/ChatSocketControllerTest.kt
git commit -m "$(cat <<'EOF'
test: WebSocket 입장 플로우 통합 테스트 추가
EOF
)"
```

---

## Task 11: WebSocket 부하테스트 스크립트 작성 (`loadtest/ws-loadtest.js`)

**Files:**
- Create: `loadtest/ws-loadtest.js`

**Interfaces:**
- Consumes: `seedFixtures`(Task 3), `encodeFrame`/`parseFrames`(Task 4), `k6/experimental/websockets`(기본 brew k6 v1.4.0, 별도 빌드 불필요 — 이미 로컬 검증됨)
- Produces: `ws_entered_response_time`(Trend), `ws_connect_success`(Rate). `--summary-export`로 JSON 저장(Task 12에서 사용).

- [ ] **Step 1: `loadtest/ws-loadtest.js` 작성**

```javascript
import { WebSocket } from 'k6/experimental/websockets';
import { Trend, Rate } from 'k6/metrics';
import { seedFixtures } from './lib/fixtures.js';
import { encodeFrame, parseFrames } from './lib/stomp.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';
const PARTY_COUNT = parseInt(__ENV.PARTY_COUNT || '80', 10);
const HOLD_SECONDS = parseInt(__ENV.HOLD_SECONDS || '20', 10);
const VUS = parseInt(__ENV.VUS || '50', 10);

export const options = {
  scenarios: {
    stage: {
      executor: 'constant-vus',
      vus: VUS,
      duration: `${HOLD_SECONDS + 15}s`,
    },
  },
  setupTimeout: '180s',
};

const enteredResponseTime = new Trend('ws_entered_response_time', true);
const connectSuccess = new Rate('ws_connect_success');

export function setup() {
  return seedFixtures(BASE_URL, PARTY_COUNT);
}

export default function (data) {
  const invite = data.invites[__VU % data.invites.length];
  const clientRequestId = `${__VU}-${__ITER}-${Date.now()}`;
  const startedAt = Date.now();
  let entered = false;

  const ws = new WebSocket(WS_URL, ['v12.stomp']);

  ws.onopen = () => {
    ws.send(encodeFrame('CONNECT', { 'accept-version': '1.2', host: 'localhost' }, ''));
  };

  ws.onmessage = (msg) => {
    const frames = parseFrames(msg.data);
    for (const frame of frames) {
      if (frame.command === 'CONNECTED') {
        ws.send(
          encodeFrame(
            'SUBSCRIBE',
            { id: 'sub-personal', destination: `/topic/parties/${invite.partyId}/personal/${clientRequestId}` },
            '',
          ),
        );
        ws.send(
          encodeFrame(
            'SUBSCRIBE',
            { id: 'sub-broadcast', destination: `/topic/parties/${invite.partyId}` },
            '',
          ),
        );
        ws.send(
          encodeFrame(
            'SEND',
            {
              destination: `/app/party-invites/${invite.token}/realtime-participants`,
              'content-type': 'application/json',
            },
            JSON.stringify({
              nickname: `u${__VU}-${__ITER}`,
              characterId: data.characterId,
              clientRequestId,
            }),
          ),
        );
      } else if (frame.command === 'MESSAGE' && !entered) {
        const body = JSON.parse(frame.body);
        if (body.event === 'entered') {
          entered = true;
          connectSuccess.add(true);
          enteredResponseTime.add(Date.now() - startedAt);
          setTimeout(() => ws.close(), HOLD_SECONDS * 1000);
        }
      } else if (frame.command === 'ERROR') {
        if (!entered) connectSuccess.add(false);
      }
    }
  };

  ws.onerror = () => {
    if (!entered) connectSuccess.add(false);
  };
}
```

- [ ] **Step 2: 소규모 스모크(vus=2)로 동작 확인** (Task 2 서버, Task 7~9 WebSocket 구현 필요, 기본 `k6` 사용 — 별도 빌드 불필요)

```bash
cd /Users/heoeunjeong/Desktop/18th-team2-server
VUS=2 HOLD_SECONDS=3 k6 run loadtest/ws-loadtest.js 2>&1 | tail -30
```

Expected: `ws_connect_success` 100%, `ws_entered_response_time` 값 존재, 에러 없이 종료.

- [ ] **Step 3: Commit**

```bash
git add loadtest/ws-loadtest.js
git commit -m "$(cat <<'EOF'
test: WebSocket 부하테스트 스크립트 추가
EOF
)"
```

---

## Task 12: WebSocket 계단식 부하테스트 실행 및 결과 저장

**Files:** 없음 (실행 태스크, `loadtest/run-stage.sh`는 Task 6에서 이미 작성됨)

**Interfaces:**
- Consumes: `loadtest/run-stage.sh`(Task 6), `loadtest/ws-loadtest.js`(Task 11)
- Produces: `loadtest/results/ws-loadtest-{50,100,200,500,1000}.json`

- [ ] **Step 1: WebSocket 계단식 실행** (Task 2 서버가 Task 7~9 반영 상태로 재기동돼 있어야 함 — `./gradlew bootRun` 재시작)

```bash
./loadtest/run-stage.sh ws-loadtest.js k6
```

Expected: 5단계 모두 완료, `loadtest/results/ws-loadtest-{50,100,200,500,1000}.json` 5개 파일 생성.

- [ ] **Step 2: 결과 파일 존재 확인**

```bash
ls -la loadtest/results/ws-loadtest-*.json
```

Expected: 5개 JSON 파일. (커밋 없음 — 결과 JSON은 `.gitignore` 대상)

---

## Task 13: 그라파나 스타일 비교 대시보드 Artifact 제작

**Files:** 없음 (Artifact 산출물, 저장소에 커밋하지 않음)

**Interfaces:**
- Consumes: `loadtest/results/sse-loadtest-*.json`, `loadtest/results/ws-loadtest-*.json` (Task 6, Task 12)

- [ ] **Step 1: 10개 결과 JSON에서 핵심 수치 추출**

```bash
cd /Users/heoeunjeong/Desktop/18th-team2-server
for f in loadtest/results/*.json; do
  echo "=== $f ==="
  python3 - "$f" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
metrics = data.get("metrics", {})
for key in ("sse_entered_response_time", "ws_entered_response_time", "sse_connect_success", "ws_connect_success"):
    if key in metrics:
        print(key, metrics[key])
PY
done
```

이 출력(avg/p90/p95/p99, rate)을 단계별(50/100/200/500/1000)로 SSE/WS 비교 표로 정리한다.

- [ ] **Step 2: `dataviz` 스킬 로드 후 Artifact 작성**

`dataviz` 스킬을 로드하고, Step 1에서 추출한 실측 수치로 다음을 포함하는 HTML Artifact를 만든다 (실측값이 없는 자리에 임의 숫자를 채우지 않는다 — Task 6/12 실행 결과가 없으면 이 태스크를 건너뛰고 실행부터 완료한다):

- 단계별(50/100/200/500/1000) 응답시간(avg/p95) 라인 차트 — SSE/WS 두 시리즈
- 단계별 에러율(연결 실패율) 라인 또는 바 차트 — SSE/WS 두 시리�즈
- "한계점"(에러율 5% 초과 또는 p95 3초 초과 첫 단계) 하이라이트
- 로컬 1대 머신 조건이라는 주의사항 명시 (design doc 9절 참고)

- [ ] **Step 3: Artifact 발행**

Artifact 도구로 발행하고 사용자에게 링크를 전달한다.

---

## Task 14: 트레이드오프 체크리스트 문서 작성

**Files:**
- Create: `docs/superpowers/specs/2026-08-18-sse-websocket-tradeoffs.md`

**Interfaces:** 없음 (최종 문서 산출물)

- [ ] **Step 1: 문서 작성**

Task 6/12 실측 결과와 아래 항목을 근거로 문서를 작성한다:

```markdown
# SSE vs WebSocket 트레이드오프

- 작성일: 2026-08-18
- 근거: loadtest/results/{sse,ws}-loadtest-*.json (로컬 1대 머신 실측)

## 실측 요약

(Task 13에서 만든 단계별 응답시간/에러율 표를 여기 옮겨 적는다)

## 트레이드오프

| 항목 | SSE (현재) | WebSocket (전환 시) |
|---|---|---|
| 방향성 | 서버→클라이언트 단방향, 클라이언트 발신은 별도 REST(`chat-messages`) | 양방향 단일 커넥션 |
| 확장성 | 단일 JVM 인메모리 레지스트리, 인스턴스 확장 시 브로드캐스트 유실 | 동일하게 `enableSimpleBroker`는 인메모리 — 다중 인스턴스 확장 시 STOMP 브로커 릴레이(RabbitMQ 등) 또는 Redis pub/sub 필요 |
| 재연결 | HTTP 기반, 브라우저 EventSource가 자동 재연결 지원(단, 이 프로젝트는 직접 fetch/POST 사용이라 수동 구현 필요) | 자동 재연결 없음, 클라이언트가 직접 구현 필요 |
| 인증 | 매 연결이 독립 HTTP 요청 — JWT/participantToken을 매번 검증 | 최초 CONNECT 시 1회 인증, 세션 유지 — 세션 하이재킹 방지책 필요 |
| 로드밸런서 | 표준 HTTP, sticky session 불필요 | sticky session(동일 인스턴스로 라우팅) 필요 — 미설정 시 재연결마다 다른 인스턴스로 갈 수 있음 |
| 인프라 복잡도 | 추가 설정 없음 | 헬스체크가 WS 핸드셰이크를 오탐하지 않도록 별도 처리 필요 |

## 전환 시 챙겨야 할 것

1. **다중 인스턴스 확장**: 현재 SSE/WS 모두 인메모리 브로드캐스트라 스케일아웃 시 그대로 못 씀 — Redis pub/sub 또는 외부 STOMP 브로커(RabbitMQ) 전제 필요
2. **로드밸런서 sticky session**: WebSocket 세션은 특정 인스턴스에 고정돼야 함
3. **재연결/네트워크 전환**: 모바일 환경에서 네트워크 전환 시 재연결 로직 클라이언트에 필요
4. **헬스체크**: `/actuator/health`와 `/ws` 핸드셰이크 경로 분리 확인
5. **일반 채팅 메시지 전송/퇴장 브로드캐스트**: 이번 구현은 입장 플로우만 WebSocket으로 만들었음 — 실제 전환 시 `SendChatMessageUseCase`/`LeaveChatUseCase`도 `ChatSseGateway` 직접 의존을 제거하고 전송 방식에 무관한 포트로 리팩터 필요
6. **버그 발견**: `RealtimeParty.MAX_PARTICIPANTS=14`가 서버에서 강제되지 않음 (표시 전용) — 전환과 무관하게 별도 수정 필요

## 결론

(Task 13 실측 결과를 바탕으로 전환 권고 여부 작성)
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-08-18-sse-websocket-tradeoffs.md
git commit -m "$(cat <<'EOF'
docs: SSE-WebSocket 트레이드오프 및 전환 체크리스트 문서 추가
EOF
)"
```

- [ ] **Step 3: 최종 정리 안내**

사용자에게 다음을 안내한다: 전체 커밋 로그 확인(`git log --oneline develop..HEAD`), `/team-pr` 스킬로 PR 생성 여부 확인 (base: `develop`), `docker ps -a --filter "label=org.testcontainers"`로 테스트 컨테이너 누수 최종 확인.
