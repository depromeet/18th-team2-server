# 카카오 톡캘린더 일정 등록 API 설계

- 작성일: 2026-08-18
- 이슈: [#251](https://github.com/depromeet/18th-team2-server/issues/251)
- 브랜치: `feature/kakao-talk-calendar`

## 배경

사용자가 참여 중인 파티 일정을 본인의 카카오톡 캘린더에 등록할 수 있게 한다.
현재 레포에는 아웃바운드 HTTP 클라이언트가 하나도 없어, 이 기능이 외부 API 연동 패턴의 첫 사례가 된다.

## 범위

**포함**
- 사용자가 명시적으로 요청할 때만 등록하는 온디맨드 엔드포인트 1개
- 같은 파티를 다시 등록하면 기존 카카오 일정을 갱신
- 파티 시작 전까지만 등록·갱신 허용
- 사유별로 구분되는 에러 응답

**제외**
- 파티 생성·참여 시점의 자동 등록
- 서버의 카카오 액세스 토큰 저장 및 갱신 (클라이언트가 매 요청 전달)
- 일정 삭제 API, 등록 상태 조회 API
- 카카오 외 캘린더 제공자

## 핵심 결정

### 토큰은 클라이언트가 전달한다

서버는 카카오 액세스 토큰을 저장하지 않는다. 클라이언트가 카카오 SDK로 톡캘린더 추가 동의를 받고,
얻은 액세스 토큰을 요청 헤더에 실어 보낸다. 서버는 그대로 카카오에 전달한다.

결과적으로 토큰 암호화 저장, 갱신 스케줄러, 추가 동의 콜백 처리가 전부 불필요해진다.

### 토큰은 body가 아니라 헤더로 받는다

`HttpExchangeLoggingFilter`는 요청 body의 `token`, `accessToken` 등 특정 필드명만 마스킹하고 헤더는 로깅하지 않는다.
body에 `kakaoAccessToken`이라는 이름으로 넣으면 마스킹 정규식에 걸리지 않아 평문으로 로그에 남는다.

### 일정 내용은 서버가 조립한다

클라이언트는 `partyId`만 보낸다. 제목·시간·설명은 서버가 파티 정보로 만든다.
클라이언트가 임의 일정 내용을 보내는 범용 등록 API가 되면 파티와 무관한 일정도 등록 가능해진다.

### 새 `calendar` feature 패키지로 분리한다

`party` 패키지는 이미 UseCase가 30개로 비대하고, 외부 채널 연동은 성격이 다르다.
cross-feature 접근은 `chat` feature가 쓰는 패턴(자기 feature의 port 인터페이스 + `infrastructure/<타feature>`의 어댑터가 상대 Service 호출)을 그대로 따른다.

## API 계약

`POST /api/v1/parties/{partyId}/talk-calendar`

| 항목 | 값 |
|---|---|
| 인증 | 기존 서비스 JWT (`@AuthenticationPrincipal UserPrincipal`) |
| 헤더 | `X-Kakao-Access-Token` (필수) |
| 요청 body | 없음 |
| 성공 응답 | 200, `data`에 `eventId`와 `updated` (기존 일정 갱신이면 `true`) |

## 컴포넌트

| 위치 | 역할 |
|---|---|
| `calendar/api/TalkCalendarController`, `TalkCalendarApi` | 엔드포인트와 Swagger 스펙 (기존 컨벤션대로 분리) |
| `calendar/application/usecase/RegisterPartyTalkCalendarEventUseCase` | `@Transactional` 경계, 흐름 제어 |
| `calendar/application/service/CalendarRegistrationService` | 등록 이력 aggregate 저장·조회 |
| `calendar/application/port/TalkCalendarPort` | 카카오 일정 생성·수정 인터페이스 |
| `calendar/application/port/PartyCalendarInfoPort` | 파티 조회와 참여자 검증 경계 |
| `calendar/domain/entity/CalendarRegistration` | 등록 이력 엔티티 |
| `calendar/infrastructure/kakao/KakaoTalkCalendarAdapter` | `RestClient` 호출, 카카오 에러를 도메인 에러로 변환 |
| `calendar/infrastructure/party/PartyCalendarInfoAdapter` | party의 `PartyService`, `ParticipantService`, `PartyInviteService` 호출 |

## 처리 흐름

1. Controller가 `userId`, `partyId`, 카카오 토큰을 UseCase에 넘긴다.
2. `PartyCalendarInfoPort`로 파티를 조회하고, 요청자가 그 파티의 호스트이거나 현재 참여 중인 참여자인지 검증한다. 파티를 나간 참여자(`hasLeft`)는 등록할 수 없다. 아니면 `PARTY_FORBIDDEN`.
3. 파티가 아직 시작되지 않았는지 확인한다. 현재 시각이 `startedAt` 이후면 `TALK_CALENDAR_PARTY_ALREADY_STARTED`.
4. 등록 이력을 조회한다.
   - 없으면 `event_id`가 빈 행을 먼저 INSERT하고 flush한다. 그 다음 카카오 일정 생성을 호출하고 받은 `event_id`를 채운다.
   - 있으면 카카오 일정 수정을 호출한다.
5. `event_id`를 응답한다.

이미 시작된 파티를 캘린더에 넣는 것은 사용자에게 의미가 없고, 지난 일정을 갱신하는 경로도 필요 없다.
그래서 생성과 갱신 모두 같은 조건으로 막는다. 파티 종료 여부는 따로 보지 않는다. 시작 전 조건이 더 강하기 때문이다.

행을 먼저 넣는 이유는 동시성 때문이다. 더블클릭으로 두 요청이 동시에 들어오면 둘 다 "이력 없음"을 보고 카카오 일정을 두 개 만든다.
UNIQUE 제약이 걸린 행을 카카오 호출보다 먼저 확보하면 두 번째 요청이 제약 위반으로 막힌다.
카카오 호출이 실패하면 트랜잭션이 롤백되어 placeholder 행도 함께 사라진다.

## 일정 내용

| 필드 | 값 |
|---|---|
| 제목 | 파티 목적(생일·이직·결혼)별 문구에 `celebrantNickname` 또는 `name`을 조합. 카카오 제한 50자에 맞춰 자른다 |
| 시작 | 파티의 `startedAt` |
| 종료 | `startedAt` + 30분 |
| 타임존 | `Asia/Seoul`. 카카오에 넘기는 시각은 UTC RFC5545 형식 |
| 설명 | `초대 링크: {URL}` 한 줄. URL 은 `PartyInviteService.findLatestUsableInviteToken` 의 토큰과 `app.web-base-url` 을 조합. 사용 가능한 초대가 없으면 빈 문자열 |

파티 엔티티의 `endedAt()`은 롤링페이퍼 열람 기한인 7일 뒤라 캘린더 일정 종료 시각으로 쓰지 않는다.

## 데이터 모델

테이블 `calendar_registration`, 마이그레이션 `V14__create_calendar_registration.sql`

| 컬럼 | 설명 |
|---|---|
| `id`, `created_at`, `updated_at` | `BaseEntity` 상속 |
| `user_id` | NOT NULL |
| `party_id` | NOT NULL |
| `provider` | `KAKAO_TALK`. 추후 다른 캘린더 제공자 대비 |
| `event_id` | 카카오 일정 ID |

UNIQUE 제약 `(user_id, party_id, provider)` 이 멱등성의 실제 방어선이다.

## 에러 처리

| 상황 | ErrorCode | HTTP |
|---|---|---|
| 헤더 누락 또는 빈 값 | `KAKAO_ACCESS_TOKEN_REQUIRED` | 400 |
| 카카오 인증 실패 | `KAKAO_TOKEN_INVALID` — 카카오 재로그인 필요 | 401 |
| 톡캘린더 동의 없음 | `KAKAO_CALENDAR_CONSENT_REQUIRED` — 추가 동의 필요 | 403 |
| 카카오 5xx 또는 타임아웃 | `KAKAO_CALENDAR_UNAVAILABLE` | 502 |
| 이미 시작된 파티 | `TALK_CALENDAR_PARTY_ALREADY_STARTED` — 시작된 파티는 등록 불가 | 409 |
| 동시 요청으로 UNIQUE 위반 | 기존 `DataIntegrityViolationExceptionExtensions`로 변환 | 409 |
| 파티 없음 / 비참여자 | 기존 `PARTY_NOT_FOUND` / `PARTY_FORBIDDEN` | 404 / 403 |

사용자가 카카오 앱에서 일정을 직접 지운 경우 수정 호출이 404로 실패한다. 이때는 기존 이력 행을 재사용해 일정을 새로 만들고, 새 일정 ID로 이력을 갱신한다.

카카오가 인증 실패와 동의 누락을 어떤 상태 코드·내부 코드로 구분하는지는 문서에 명시돼 있지 않다. 구현 중 실제 응답으로 확정한다.

## 외부 API 사용

| 용도 | 엔드포인트 |
|---|---|
| 일정 생성 | `POST https://kapi.kakao.com/v2/api/calendar/create/event` |
| 일정 수정 | `POST https://kapi.kakao.com/v2/api/calendar/update/event/host` |

두 호출 모두 `Authorization: Bearer` 헤더를 쓰고, form-urlencoded 바디의 `event` 파라미터에 일정 정보를 JSON 문자열로 넣는다.
생성 응답에 `event_id`가 담겨 온다. 필요한 동의항목의 영문 키(통상 `talk_calendar_task`)는 카카오 개발자 콘솔에서 확인해 확정한다.

UseCase의 트랜잭션 안에서 외부 HTTP를 호출하게 되므로 `RestClient`에 connect 2초, read 5초 타임아웃을 건다.
카카오가 느릴 때 DB 커넥션 점유 시간을 제한하기 위함이다.

## 설정 추가

| 키 | 용도 |
|---|---|
| `kakao.talk-calendar.base-url` | 기본값 `https://kapi.kakao.com`. 테스트에서 오버라이드 |
| `app.web-base-url` | 일정 설명의 초대 링크 조립. `support.chat-url`처럼 환경별 yml에서 override |

## 테스트 전략

`docs/testing-rules.md`를 따른다.

- `KakaoTalkCalendarAdapter`: `MockRestServiceServer`로 form 바디, 헤더, 에러 매핑을 검증
- `RegisterPartyTalkCalendarEventUseCase`: Port 스텁으로 생성·갱신 분기, 권한 실패, 시작 시각 경계, 일정 404 재시도를 검증
- 통합 테스트: `TestcontainersConfiguration` 경유로 엔드포인트 동작과 UNIQUE 제약을 검증

## 알려진 부채

`PartyService`에 `requireParty(partyId)` public 메서드를 추가한다. 기존 공개 메서드가 전부 `RealtimeParty` 전용이고 일반 `Party` 조회는 private이기 때문이다.
이러면 `PartyService`의 public 메서드가 8개가 되어 아키텍처 규칙(5개 이내)에서 더 멀어진다. 이번 작업 범위를 넘어서므로 감수하고 넘어간다.
