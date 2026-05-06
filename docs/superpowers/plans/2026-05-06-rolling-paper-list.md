# Rolling Paper List API Implementation Plan

## Summary

롤링페이퍼 목록 조회 API를 참가자용과 주최자용으로 분리해 추가한다.

- 참가자용은 초대 토큰 기반 공개 조회다.
- 주최자용은 인증된 파티 소유자만 조회할 수 있다.
- 두 API는 공통 목록 item을 사용한다.
- 목록은 표준 page 기반 페이지네이션으로 조회하고, 응답 시각은 기존 API와 맞춰 KST 기준 `LocalDateTime` 문자열을 유지한다.

Spec: `docs/superpowers/specs/2026-05-05-rolling-paper-list-design.md`

## API Contract

### Participant List

`GET /api/v1/party-invites/{inviteToken}/rolling-papers?page=1`

- 인증 없이 조회할 수 있다.
- 유효한 Bearer token이 있으면 인증 사용자로 통과한다.
- 잘못된 Bearer token이면 `AUTH_INVALID_TOKEN`, 401로 응답한다.
- 초대 토큰이 없으면 `PARTY_NOT_FOUND`, 404로 응답한다.
- 초대 토큰 만료 여부와 파티 자체 종료 여부는 목록 조회를 막지 않는다.
- `PAPER_ONLY`는 `liveEndAt = null`로 응답한다.
- 참가자 응답에는 `totalCount`, `pageSize`, `partyEndAt`을 포함하지 않는다.

Response data:

```json
{
  "partyOption": "REALTIME",
  "liveEndAt": "2026-05-05T22:10:00",
  "page": 1,
  "totalPages": 2,
  "hasNext": true,
  "items": [
    {
      "rollingPaperId": 10,
      "writerNickname": "축하요정",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

### Owner List

`GET /api/v1/parties/{partyId}/rolling-papers?page=1`

- 인증 필수다.
- `party.ownerId == principal.userId`가 아니면 `PARTY_FORBIDDEN`, 403으로 응답한다.
- 주최자 열람 가능 시각 전이면 `ROLLING_PAPER_NOT_VIEWABLE`, 403으로 응답한다.
- `partyEndAt`은 `Party.endedAt()` 기준으로 응답한다.

Response data:

```json
{
  "celebrantNickname": "홍길동",
  "partyEndAt": "2026-05-12T14:30:00",
  "page": 1,
  "totalCount": 8,
  "totalPages": 2,
  "hasNext": true,
  "items": [
    {
      "rollingPaperId": 10,
      "writerNickname": "축하요정",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

## Implementation Steps

1. 도메인 시간 정책을 정리한다.
   - `Party.hostViewableAt()`을 하위 타입별로 구현한다.
   - `Party.canHostViewRollingPapers(now)`는 `now == hostViewableAt()`을 열람 가능으로 본다.
   - `RealtimeParty.hostViewableAt()`은 `startedAt + LIVE_DURATION_MINUTES`다.
   - `PaperOnlyParty.hostViewableAt()`은 `startedAt` 날짜의 22:00이다.
   - 단, `startedAt`이 해당 날짜 22:00 이상이면 다음날 22:00으로 계산한다.

2. 시간 계산 정책을 적용한다.
   - 프로젝트 기존 스타일에 맞춰 별도 `Clock` bean은 추가하지 않는다.
   - 목록 UseCase는 열람 검증 시점에 `LocalDateTime.now()`를 사용한다.

3. 목록 조회 기반을 추가한다.
   - `RollingPaperRepository.findAllByParty(..., Pageable): Page<RollingPaper>`를 추가한다.
   - 정렬은 `createdAt DESC, id DESC`, 페이지 크기는 7로 고정한다.
   - `page < 1`은 1로 보정한다.
   - `totalCount = 0`이면 `totalPages = 0`, `items = []`, `hasNext = false`다.
   - `page > totalPages`이면 요청 page를 유지하고 `items = []`, `hasNext = false`다.
   - wrapper 이미지는 bulk 조회 후 `sortOrder ASC` 첫 번째 이미지를 매핑한다.

4. DTO, UseCase, Controller를 추가한다.
   - 참가자용 GET 경로는 `SecurityConfig`에 method/path-specific `permitAll`로 추가한다.
   - 주최자용 GET 경로는 기본 authenticated 정책을 사용한다.
   - Swagger에는 200, 401, 403, 404, 500과 주요 ErrorCode 예시를 명시한다.

## Test Plan

- 참가자용 목록
  - 인증 없이 조회 성공
  - 유효 Bearer token 포함 조회 성공
  - invalid Bearer token 401
  - 없는 inviteToken 404
  - `REALTIME`이면 `liveEndAt = startedAt + LIVE_DURATION_MINUTES`
  - `PAPER_ONLY`이면 `liveEndAt = null`
  - 파티 자체 종료 후에도 조회 성공
  - `totalCount`는 응답하지 않음

- 주최자용 목록
  - 소유자 조회 성공
  - 소유자가 아니면 403 `PARTY_FORBIDDEN`
  - 열람 가능 전이면 403 `ROLLING_PAPER_NOT_VIEWABLE`
  - `now == hostViewableAt()`이면 조회 성공
  - `PAPER_ONLY`는 22:00 기준 열람 가능
  - `REALTIME`은 live 종료 시각 기준 열람 가능
  - `celebrantNickname`, `partyEndAt`, `totalCount`, `totalPages` 응답 확인

- 공통 목록/페이지네이션
  - 최신순 `createdAt DESC, id DESC`
  - 7개 고정 페이지
  - `page < 1`은 1로 보정
  - 빈 목록: `totalPages = 0`, `items = []`
  - 초과 페이지: 요청 page 유지, `items = []`, `hasNext = false`
  - wrapper 이미지는 bulk 조회 결과 중 `sortOrder ASC` 첫 번째 URL 사용
  - 이미지 없으면 `wrapperImageUrl = null`

## Verification

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperListControllerTest
./gradlew test --tests com.team2.server.party.entity.PartyStatusTest
./gradlew test
./gradlew ktlintCheck
```
