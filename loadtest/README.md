# 실시간 파티 부하테스트 (SSE vs WebSocket)

로컬 1대 머신에서 k6 부하 클라이언트와 Spring 서버가 자원을 공유하는 조건으로
SSE와 WebSocket(STOMP) 각각의 동시접속 한계·응답시간을 측정하고 비교한다.

## 사전 준비

1. MySQL 기동: `docker compose -f docker/docker-compose.local.yml up -d`
2. 서버 기동 — **부하테스트용 커넥션 풀 크기를 명시적으로 지정해야 한다**:

   ```bash
   SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=80 ./gradlew bootRun
   ```

   (기본 `local` 프로파일, `localhost:8080`)

   `application-local.yml`의 풀 크기 기본값은 Spring Boot 기본값(10)이다. 이는 dev/prod와
   같은 값으로, 커넥션 고갈 문제를 로컬에서도 같은 지점에서 재현하기 위한 의도적 설정이다.
   부하테스트에서는 풀이 병목이 되면 전송 계층(SSE vs WS) 자체의 한계를 측정할 수 없으므로,
   위와 같이 환경변수로 80을 **옵트인**해야 `loadtest/results/`의 기존 수치가 재현된다.
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
