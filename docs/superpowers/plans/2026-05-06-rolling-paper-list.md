# Rolling Paper List API Implementation Plan

## Summary

롤링페이퍼 목록 조회 API를 참가자용과 주최자용으로 분리해 유지하되, 목록 item을 상세 오버레이의 데이터 소스로 확장한다.

- 참가자용은 초대 토큰 기반 공개 조회다.
- 주최자용은 인증된 파티 소유자만 조회할 수 있다.
- 참가자용과 주최자용은 화면 권한에 맞춰 서로 다른 목록 item을 사용한다.
- 상세 오버레이는 기본적으로 목록 page 응답으로 렌더링한다.
- 주최자용 상세 조회 API는 딥링크, 새로고침 복구, 운영성 조회 같은 보조 플로우를 위해 유지한다.
- 목록은 표준 page 기반 페이지네이션으로 조회하고, `PAGE_SIZE = 7`을 사용한다.
- `content`는 작성 API의 기존 제한과 동일하게 최대 100자다.
- 응답 시각은 기존 API와 맞춰 KST 기준 `LocalDateTime` 문자열을 유지한다.

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
- 참가자 응답에는 상세 오버레이의 `1 / N` 표시에 필요한 `totalCount`를 포함한다.
- 참가자 응답에는 `pageSize`, `partyEndAt`을 포함하지 않는다.
- 참가자용 상세 단건 조회 API는 이번 계약에 추가하지 않는다. 특정 롤링페이퍼 ID로 바로 진입하는 참가자 딥링크/복구 요구가 생기면 별도 계약으로 추가한다.

Response data:

```json
{
  "partyOption": "REALTIME",
  "liveEndAt": "2026-05-05T22:10:00",
  "pageInfo": {
    "page": 1,
    "totalCount": 12,
    "totalPages": 2,
    "hasNext": true
  },
  "items": [
    {
      "rollingPaperId": 10,
      "writerNickname": "축하요정",
      "toppingImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
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
  "pageInfo": {
    "page": 1,
    "totalCount": 8,
    "totalPages": 2,
    "hasNext": true
  },
  "items": [
    {
      "rollingPaperId": 10,
      "position": 1,
      "writerNickname": "축하요정",
      "content": "생일 축하해요!",
      "toppingImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

### Owner Detail

`GET /api/v1/parties/{partyId}/rolling-papers/{rollingPaperId}`

- 인증 필수다.
- `party.ownerId == principal.userId`가 아니면 `PARTY_FORBIDDEN`, 403으로 응답한다.
- 주최자 열람 가능 시각 전이면 `ROLLING_PAPER_NOT_VIEWABLE`, 403으로 응답한다.
- 목록 화면에서 토핑을 눌러 상세 오버레이를 여는 기본 플로우에는 사용하지 않는다.
- 특정 롤링페이퍼 ID로 바로 진입해야 하는 딥링크, 푸시 알림, 새로고침 복구, 운영성 조회 같은 보조 플로우에 사용한다.
- 상세 오버레이의 좌우 이동은 목록 page 캐시와 인접 page 조회로 처리한다.
- 상세 API는 단건 복구에 필요한 `content`, `writerNickname`, `position`, `totalCount`만 제공하고 이전/다음 롤링페이퍼 ID는 제공하지 않는다.

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
   - 정렬은 `createdAt DESC, id DESC`, 페이지 크기는 `PAGE_SIZE = 7`로 고정한다.
   - `page < 1`은 1로 보정한다.
   - `totalCount = 0`이면 `totalPages = 0`, `items = []`, `hasNext = false`다.
   - `page > totalPages`이면 요청 page를 유지하고 `items = []`, `hasNext = false`다.
   - 참가자용과 주최자용 모두 `pageInfo.totalCount`를 응답한다.
   - item의 `position`은 `(page - 1) * PAGE_SIZE + index + 1`로 계산한다.
   - 참가자용 item에는 `position`, `content`를 포함하지 않는다.
   - 주최자용 item에는 상세 오버레이용 `position`, `content`를 포함한다.
   - 주최자용 item의 `content`는 작성 API의 `@Size(max = 100)` 제한을 그대로 따른다.
   - `position`과 `totalCount`는 목록 응답 시점의 snapshot 기준이며, 상세 오버레이를 보는 동안 새 롤링페이퍼가 추가되는 eventual consistency는 허용한다.
   - 토핑 이미지는 bulk 조회 후 `sortOrder ASC` 첫 번째 이미지를 매핑한다.

4. DTO, UseCase, Controller를 추가한다.
   - 참가자용 GET 경로는 `SecurityConfig`에 method/path-specific `permitAll`로 추가한다.
   - 주최자용 GET 경로는 기본 authenticated 정책을 사용한다.
   - Swagger에는 200, 401, 403, 404, 500과 주요 ErrorCode 예시를 명시한다.

5. 주최자용 상세 조회 API를 보조 플로우로 정리한다.
   - 기본 상세 오버레이 플로우는 목록 응답을 사용하도록 문서와 Swagger 설명을 맞춘다.
   - 상세 API는 직접 ID 진입과 복구 조회 용도임을 명시한다.
   - 상세 API 삭제는 딥링크/복구 요구가 사라진 뒤 별도 과제로 판단한다.

## Test Plan

- 참가자용 목록
  - 인증 없이 조회 성공
  - 유효 Bearer token 포함 조회 성공
  - invalid Bearer token 401
  - 없는 inviteToken 404
  - `REALTIME`이면 `liveEndAt = startedAt + LIVE_DURATION_MINUTES`
  - `PAPER_ONLY`이면 `liveEndAt = null`
  - 파티 자체 종료 후에도 조회 성공
  - `pageInfo.totalCount` 응답
  - item에 `position`, `content`를 응답하지 않는다.

- 주최자용 목록
  - 소유자 조회 성공
  - 소유자가 아니면 403 `PARTY_FORBIDDEN`
  - 열람 가능 전이면 403 `ROLLING_PAPER_NOT_VIEWABLE`
  - `now == hostViewableAt()`이면 조회 성공
  - `PAPER_ONLY`는 22:00 기준 열람 가능
  - `REALTIME`은 live 종료 시각 기준 열람 가능
  - `celebrantNickname`, `partyEndAt`, `pageInfo.totalCount`, `pageInfo.totalPages` 응답 확인
  - item에 `position`, `content` 응답
  - item `content`는 최대 100자 계약을 따른다.

- 공통 목록/페이지네이션
  - 최신순 `createdAt DESC, id DESC`
  - `PAGE_SIZE = 7` 고정 페이지
  - `page < 1`은 1로 보정
  - 빈 목록: `totalPages = 0`, `items = []`
  - 초과 페이지: 요청 page 유지, `items = []`, `hasNext = false`
  - `position`은 전체 최신순 목록 기준 순번
  - 조회 이후 새 롤링페이퍼 추가로 인한 cached `position` / `totalCount`의 eventual consistency 허용
  - 토핑 이미지는 bulk 조회 결과 중 `sortOrder ASC` 첫 번째 URL 사용
  - 기본 토핑 seed 기준 `toppingImageUrl`은 non-null이다.

- 주최자용 상세
  - 특정 롤링페이퍼 ID로 상세 조회 성공
  - `rollingPaperId`, `content`, `writerNickname`, `position`, `totalCount` 응답
  - `previousRollingPaperId`, `nextRollingPaperId`는 응답하지 않음
  - 해당 파티의 롤링페이퍼가 아니면 404
  - 소유자가 아니면 403
  - 열람 가능 전이면 403

## Verification

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperListControllerTest
./gradlew test --tests com.team2.server.party.entity.PartyStatusTest
./gradlew test
./gradlew ktlintCheck
```
