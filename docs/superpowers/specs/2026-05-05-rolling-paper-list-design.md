# Rolling Paper List API Design

- 작성일: 2026-05-05
- 기준 브랜치: `feature/rolling-paper-list-detail-contract`
- 목적: 롤링페이퍼 목록 조회 API의 참가자용/주최자용 계약과 상세 오버레이 데이터 제공 방식 정리
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 1. 결정 요약

롤링페이퍼 목록 조회 API는 참가자용과 주최자용을 분리한다.

상세 오버레이는 기본적으로 목록 조회 응답을 데이터 소스로 사용한다.

이유:

- 참가자 화면은 초대 링크 기반 흐름이고, 주최자 화면은 인증된 파티 소유자 흐름이다.
- 두 화면은 목록 카드 필드는 같지만, 열람 조건과 화면 메타가 다르다.
- 주최자 화면은 아직 열람 가능 시간이 아니면 예외로 처리하는 편이 자연스럽다.
- `isOwner`, `viewerRole` 같은 분기 필드를 하나의 응답에 섞지 않아도 된다.
- 목록 화면에서 토핑을 누르면 같은 컬렉션을 상세 오버레이로 보여주는 UX이므로, 상세 본문을 별도 API로 매번 다시 조회하지 않는다.
- 상세 오버레이의 좌우 이동은 현재 로딩된 목록 page의 `items`로 처리하고, page 경계를 넘을 때 다음 또는 이전 page를 추가 조회한다.

목록 item 응답은 두 API에서 공통으로 사용한다.

공통 item:

- `rollingPaperId`
- `position`
- `writerNickname`
- `content`
- `wrapperImageUrl`

정렬과 페이지네이션도 두 API가 같은 규칙을 사용한다.

- 최신순
- 한 페이지 기준 7개
- 표준 페이지네이션을 사용한다.

---

## 2. 사용자 흐름

### 2-1. 참가자용 목록

1. 사용자가 초대 링크로 진입한다.
2. 사용자가 롤링페이퍼를 작성하거나, 이미 작성한 이후 받은 롤링페이퍼 목록 화면으로 이동한다.
3. 프론트는 `inviteToken`으로 참가자용 목록 API를 호출한다.
4. 프론트는 목록 item을 최신순으로 렌더링한다.
5. 사용자가 토핑을 누르면 이미 받은 목록 item의 `content`와 `writerNickname`으로 상세 오버레이를 연다.
6. 상세 오버레이에서 좌우 이동 시 현재 page 안에서는 추가 API 호출 없이 이동하고, page 경계를 넘으면 인접 page를 조회한다.

참가자용 화면에서는 파티 자체 종료 후에도 롤링페이퍼 조회를 허용한다.
다만 이 정책은 확정 요구사항이 바뀌면 조정될 수 있다.

이유:

- 현재 파티 상태 문서 기준으로 파티 종료 후에는 작성은 불가능하지만 조회는 가능하다.
- 파티 종료 후 목록까지 막으면 사용자가 받은 롤링페이퍼를 다시 볼 수 없는 정책이 된다.

### 2-2. 주최자용 목록

1. 주최자가 파티 상세 또는 생성 후 관리 화면에서 롤링페이퍼 목록 화면으로 진입한다.
2. 프론트는 인증 토큰과 `partyId`로 주최자용 목록 API를 호출한다.
3. 서버는 현재 사용자가 해당 파티의 소유자인지 검증한다.
4. 서버는 주최자가 롤링페이퍼를 열람할 수 있는 시점인지 검증한다.
5. 열람 가능하면 목록, 상세 오버레이에 필요한 item 데이터, `partyEndAt`을 내려준다.
6. 열람 불가하면 예외로 응답한다.
7. 사용자가 토핑을 누르면 프론트는 목록 item 데이터로 상세 오버레이를 연다.

주최자용 화면의 공유하기 버튼은 목록 API에 포함하지 않는다.

공유하기 버튼 클릭 시 기존 초대 링크 API를 별도로 호출한다.

```http
POST /api/v1/parties/{partyId}/invite-link
```

이 API는 현재 유효한 초대 토큰이 있으면 재사용하고, 없으면 새로 생성해 `token`을 반환한다.

---

## 3. API 계약

아래 JSON 예시는 `ApiResponse.data` payload 기준이다.
실제 HTTP 응답은 기존 컨트롤러 관례대로 공통 `ApiResponse`로 감싼다.

### 3-1. 참가자용 목록 조회

```http
GET /api/v1/party-invites/{inviteToken}/rolling-papers?page=1
```

인증:

- 공개 조회를 허용한다.
- Authorization header가 없어도 조회 가능하다.
- 유효한 Bearer token이 있으면 인증 사용자로 처리한다.
- 잘못된 Bearer token은 `AUTH_INVALID_TOKEN`, 401로 처리한다.

응답:

```json
{
  "partyOption": "REALTIME",
  "liveEndAt": "2026-05-05T22:10:00",
  "page": 1,
  "totalCount": 12,
  "totalPages": 2,
  "hasNext": true,
  "items": [
    {
      "rollingPaperId": 10,
      "position": 1,
      "writerNickname": "축하요정",
      "content": "생일 축하해요!",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

`PAPER_ONLY` 응답:

```json
{
  "partyOption": "PAPER_ONLY",
  "liveEndAt": null,
  "page": 1,
  "totalCount": 12,
  "totalPages": 2,
  "hasNext": true,
  "items": [
    {
      "rollingPaperId": 10,
      "position": 1,
      "writerNickname": "축하요정",
      "content": "생일 축하해요!",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

필드:

| 필드 | 설명 |
|---|---|
| `partyOption` | `REALTIME`, `PAPER_ONLY`. `liveEndAt = null`의 의미를 명확히 하기 위해 유지한다. |
| `liveEndAt` | 실시간 파티 종료 시각. `PAPER_ONLY`이면 `null`. |
| `page` | 요청한 페이지 번호. 1부터 시작한다. |
| `totalCount` | 전체 롤링페이퍼 수. 상세 오버레이의 `1 / 12` 표시에 사용한다. |
| `totalPages` | 페이지 번호 UI 계산용 전체 페이지 수. |
| `hasNext` | 다음 페이지 존재 여부. |
| `items` | 롤링페이퍼 카드 목록. |
| `items[].rollingPaperId` | 롤링페이퍼 식별자. |
| `items[].position` | 최신순 기준 현재 롤링페이퍼 순번. 1부터 시작한다. |
| `items[].writerNickname` | 롤링페이퍼 작성 당시 닉네임 스냅샷. |
| `items[].content` | 롤링페이퍼 상세 오버레이에 표시할 본문. |
| `items[].wrapperImageUrl` | 카드 렌더링용 래퍼 이미지 URL. 이미지가 없으면 `null`. |

응답에서 제외하는 필드:

| 제외 필드 | 제외 이유 |
|---|---|
| `pageSize` | 한 페이지 기준은 7개로 고정이므로 응답에 반복해서 내려주지 않는다. |
| `partyEndAt` | 참가자용 목록 화면에서는 파티 자체 종료 시각을 표시하지 않는다. |

참가자용은 실시간 파티 종료 여부 boolean 대신 `liveEndAt`을 내려준다.

이유:

- `liveEndAt`은 서버 응답 시점의 boolean보다 캐시와 시간 경과에 덜 취약하다.
- 프론트는 화면 진입 시 한 번 현재 시각과 `liveEndAt`을 비교해 필요한 분기를 계산할 수 있다.
- 기존 초대장 조회 API도 실시간 파티 종료 판단 기준으로 `realtimeSchedule.liveEndAt`을 내려준다.

### 3-2. 주최자용 목록 조회

```http
GET /api/v1/parties/{partyId}/rolling-papers?page=1
```

인증:

- 인증 필수.
- `party.ownerId == principal.userId`가 아니면 403.
- 주최자와 참가자 개념을 응답 boolean으로 섞지 않는다.

응답:

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
      "position": 1,
      "writerNickname": "축하요정",
      "content": "생일 축하해요!",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
    }
  ]
}
```

필드:

| 필드 | 설명 |
|---|---|
| `celebrantNickname` | 파티 주인공 이름. 현재 `Party.celebrantNickname` 기준. |
| `partyEndAt` | 파티 자체 종료 시각. `Party.endedAt()` 기준. |
| `page` | 요청한 페이지 번호. 1부터 시작한다. |
| `totalCount` | 주최자 화면에서 표시할 전체 롤링페이퍼 개수. |
| `totalPages` | 페이지 번호 UI 계산용 전체 페이지 수. |
| `hasNext` | 다음 페이지 존재 여부. |
| `items` | 롤링페이퍼 카드 목록. |
| `items[].rollingPaperId` | 롤링페이퍼 식별자. |
| `items[].position` | 최신순 기준 현재 롤링페이퍼 순번. 1부터 시작한다. |
| `items[].writerNickname` | 롤링페이퍼 작성 당시 닉네임 스냅샷. |
| `items[].content` | 롤링페이퍼 상세 오버레이에 표시할 본문. |
| `items[].wrapperImageUrl` | 카드 렌더링용 래퍼 이미지 URL. 이미지가 없으면 `null`. |

응답에서 제외하는 필드:

| 제외 필드 | 제외 이유 |
|---|---|
| `partyOption` | 주최자 목록 화면의 응답 요구사항에는 필요하지 않다. 열람 가능 여부는 서버가 검증한다. |
| `pageSize` | 한 페이지 기준은 7개로 고정이므로 응답에 반복해서 내려주지 않는다. |
| `inviteToken`, `shareLink` | 공유하기 버튼 클릭 시 기존 초대 링크 API를 별도로 호출한다. |

### 3-3. 주최자용 상세 조회

```http
GET /api/v1/parties/{partyId}/rolling-papers/{rollingPaperId}
```

인증:

- 인증 필수.
- `party.ownerId == principal.userId`가 아니면 403.
- 주최자 열람 가능 시각 전이면 403.

역할:

- 목록 화면에서 토핑을 눌러 상세 오버레이를 여는 기본 플로우에는 사용하지 않는다.
- 특정 롤링페이퍼 ID로 바로 상세를 열어야 하는 딥링크, 푸시 알림, 새로고침 복구, 운영성 조회 같은 보조 플로우를 위해 유지한다.
- 상세 오버레이의 좌우 이동은 이 API의 이전/다음 ID에 의존하지 않고 목록 page 캐시와 인접 page 조회로 처리한다.

응답:

```json
{
  "rollingPaperId": 10,
  "content": "생일 축하해요!",
  "writerNickname": "축하요정",
  "position": 1,
  "totalCount": 12,
  "previousRollingPaperId": null,
  "nextRollingPaperId": 9
}
```

향후 딥링크/복구 요구가 사라지면 상세 API는 제거할 수 있다.

---

## 4. 열람 조건

### 4-1. 참가자용

참가자용 목록은 파티 자체 종료 후에도 조회 가능하다.

조회 가능 여부를 파티 종료 시각으로 막지 않는다.

없는 초대 토큰이면 `PARTY_NOT_FOUND`를 반환한다.

### 4-2. 주최자용

주최자용 목록은 주최자가 롤링페이퍼를 열람할 수 있는 시점 이후에만 조회 가능하다.

열람 가능 전이면 403 예외로 처리한다.

```text
ROLLING_PAPER_NOT_VIEWABLE
```

주최자 열람 가능 시점:

| 파티 타입 | 조건 |
|---|---|
| `REALTIME` | 실시간 파티 종료 이후 |
| `PAPER_ONLY` | 롤링페이퍼 전용 파티 공개 시각 이후 |

`Party` 추상 타입에 주최자 열람 가능 시각과 검증 메서드를 둔다.

```kotlin
abstract fun hostViewableAt(): LocalDateTime

fun canHostViewRollingPapers(now: LocalDateTime): Boolean =
    !hostViewableAt().isAfter(now)
```

경계값은 inclusive로 처리한다. 즉 `now == hostViewableAt()`이면 열람 가능하다.

동일 요청 안에서는 `now`를 한 번만 계산해 열람 검증과 응답 계산에 같은 값을 사용한다.
구현 시 프로젝트 기존 스타일에 맞춰 별도 `Clock` bean은 추가하지 않고, 목록 UseCase에서 `LocalDateTime.now()`를 사용한다.

실시간 파티 종료 시각:

```text
RealtimeParty.hostViewableAt() = startedAt + RealtimeParty.LIVE_DURATION_MINUTES
```

롤링페이퍼 전용 파티 공개 시각은 기본적으로 `startedAt` 날짜의 22:00으로 계산한다.
단, `startedAt`이 해당 날짜 22:00 이상이면 이미 공개 기준 시각을 지난 상태이므로 다음날 22:00으로 계산한다.

현재 코드:

- `PaperOnlyParty.startedAt = startedDate.atStartOfDay()`
- `PaperOnlyParty.status()` 내부의 `openTime`은 현재 `startedAt`을 그대로 사용한다.
- 이 `openTime`은 롤링페이퍼 전용 파티 자체가 열리는 시각이다.
- 주최자가 받은 롤링페이퍼 목록을 열람할 수 있는 시각과는 별도 정책이다.

기획 요구:

- 롤링페이퍼 전용 파티는 생성 당일 밤 10시부터 주최자가 열람 가능

결정:

- `startedAt`은 기존 생성 계약대로 00:00을 유지한다.
- `PaperOnlyParty.status()`의 `OPEN`은 파티 open 상태이므로 주최자 롤링페이퍼 열람 가능 여부로 재사용하지 않는다.
- `PaperOnlyParty.hostViewableAt()`은 `startedAt` 날짜의 22:00을 반환한다.
- `startedAt`이 해당 날짜 22:00 이상이면 다음날 22:00을 반환한다.
- 주최자 목록 API는 `Party.canHostViewRollingPapers(now)`만 사용해 열람 가능 여부를 검증한다.

```kotlin
override fun hostViewableAt(): LocalDateTime =
    startedAt.toLocalDate().atTime(22, 0).let { targetTime ->
        if (startedAt.isBefore(targetTime)) targetTime else targetTime.plusDays(1)
    }
```

향후 파티 open 상태와 롤링페이퍼 열람 상태가 더 많은 화면에서 함께 필요해지면, 파티 상태와 롤링페이퍼 열람 상태를 별도 도메인 개념으로 분리한다.

---

## 5. 페이지네이션 규칙

페이지 요청은 1부터 시작하고, `page`가 1보다 작으면 서버에서 1로 보정한다.
한 페이지 기준 개수는 7개 고정이며, 정렬은 `createdAt DESC, id DESC`다.

서버는 `totalPages` 계산과 상세 오버레이의 `1 / N` 표시를 위해 `totalCount`를 조회한다.
참가자용과 주최자용 응답 모두 `totalCount`를 내려준다.

각 item의 `position`은 최신순 전체 목록 기준 1부터 시작한다.
목록 page 응답에서는 별도 count 쿼리로 개별 위치를 계산하지 않고, 현재 page와 index로 계산한다.

```text
position = (page - 1) * 7 + itemIndex + 1
```

응답 invariant:

- `totalCount = 0`이면 `totalPages = 0`, `hasNext = false`, `items = []`로 응답한다.
- 빈 목록이어도 `page`는 보정된 요청 page를 그대로 응답한다.
- `page > totalPages`이면 `page`는 요청값 그대로 응답하고, `items = []`, `hasNext = false`로 응답한다.
- 서버는 상한 초과 page를 마지막 페이지로 보정하지 않는다.
- `items = []`이면 item별 `position`도 응답하지 않는다.

커서 기반 페이지네이션은 이번 API 계약에서는 사용하지 않는다.

- 커서 방식은 새 롤링페이퍼가 계속 추가되는 피드에서 중복/누락을 줄이는 데 유리하다.
- 하지만 이번 화면은 페이지 번호와 `totalPages`를 보여주므로, `1, 2, 3` 같은 임의 페이지 이동과 전체 페이지 수 표시가 가능한 page 기반 조회가 더 맞다.
- 따라서 이번 목록은 표준 page 기반 조회를 사용한다.
- 새 데이터 유입에 따른 offset 방식의 중복/누락 가능성은 남지만, 롤링페이퍼 목록은 파티 단위 목록이고 페이지 번호 UI가 필요하므로 이 tradeoff를 수용한다.

---

## 6. 시간 정책

응답의 시간 필드는 KST 기준 `LocalDateTime` 문자열로 내려준다.

예:

```json
{
  "liveEndAt": "2026-05-05T22:10:00",
  "partyEndAt": "2026-05-12T14:30:00"
}
```

정책:

- 서버와 클라이언트는 timezone offset이 없는 시간 문자열을 KST로 해석한다.
- 이번 API는 기존 API의 `LocalDateTime` 직렬화 방식과 맞춘다.
- 추후 시간 API를 전역 기준으로 정리할 경우 `OffsetDateTime(+09:00)` 또는 UTC `Instant`로 전환하는 것을 별도 과제로 다룬다.

파티 자체 종료 시각은 `Party.endedAt()`을 사용한다.

```text
Party.endedAt() = Party.startedAt + Party.ENDED_AFTER_DAYS
```

`Party.ENDED_AFTER_DAYS`의 현재 값은 7일이다.

---

## 7. 이미지 URL 정책

목록 item은 `wrapperImageUrl`과 상세 오버레이에 필요한 `content`를 내려준다.

이유:

- 목록 화면은 카드를 렌더링하는 화면이므로 프론트가 별도 래퍼 캐시를 강제하지 않는 편이 좋다.
- 상세 오버레이는 같은 롤링페이퍼 컬렉션을 확대 표시하는 화면이므로 본문을 목록 page 응답에 함께 포함하는 편이 호출 수와 프론트 상태 관리를 줄인다.
- 이미 래퍼 조회 API도 `wrapperId`, `wrapperImageUrl`을 내려주는 계약이다.
- 작성 API는 `wrapperId`를 받고, 목록 API는 렌더링용 이미지 URL을 내려주는 역할 분리가 명확하다.

조회 기준:

```text
image.target_type = ROLLING_PAPER_WRAPPER
image.target_id = rolling_paper.wrapper_id
sort_order ASC 첫 번째 이미지
```

래퍼 이미지가 없으면 `wrapperImageUrl = null`로 응답한다.

구현 기준:

- 페이지에 포함된 롤링페이퍼의 `wrapper_id` 목록을 모은다.
- `ImageTargetType.ROLLING_PAPER_WRAPPER`와 `wrapper_id IN (...)` 조건으로 이미지를 한 번에 조회한다.
- 같은 래퍼에 이미지가 여러 개 있으면 `sort_order ASC` 첫 번째 이미지만 사용한다.
- 롤링페이퍼 item마다 이미지를 개별 조회하지 않는다.

---

## 8. ErrorCode

추가 후보:

```kotlin
ROLLING_PAPER_NOT_VIEWABLE(HttpStatus.FORBIDDEN, "아직 롤링페이퍼를 확인할 수 없습니다")
```

기존 ErrorCode 재사용:

| 상황 | ErrorCode | HTTP status |
|---|---|---|
| 초대 토큰 없음 | `PARTY_NOT_FOUND` | 404 |
| 파티 없음 | `PARTY_NOT_FOUND` | 404 |
| 주최자용 목록을 소유자가 아닌 사용자가 조회 | `PARTY_FORBIDDEN` | 403 |
| 주최자용 목록 열람 가능 전 | `ROLLING_PAPER_NOT_VIEWABLE` | 403 |
| invalid Bearer token | `AUTH_INVALID_TOKEN` | 401 |

---

## 9. 테스트 계획

참가자용 목록:

- 초대 토큰으로 목록 조회 성공
- `partyOption`, `liveEndAt`, `page`, `totalCount`, `totalPages`, `hasNext`, `items` 응답
- `PAPER_ONLY`이면 `liveEndAt = null`
- `REALTIME`이면 `liveEndAt = startedAt + RealtimeParty.LIVE_DURATION_MINUTES`
- 파티 자체 종료 이후에도 목록 조회 성공. 단, 정책 변경 가능성이 있음을 문서에 남김
- 없는 초대 토큰이면 404
- invalid Bearer token이면 401

주최자용 목록:

- 파티 소유자가 목록 조회 성공
- 파티 소유자가 아니면 403
- 주최자 열람 가능 전이면 403 `ROLLING_PAPER_NOT_VIEWABLE`
- 주최자 열람 가능 후이면 목록 조회 성공
- `celebrantNickname`, `partyEndAt`, `page`, `totalCount`, `totalPages`, `hasNext`, `items` 응답
- `PAPER_ONLY` 주최자 열람 가능 시각은 `PaperOnlyParty.hostViewableAt()` 기준
- `REALTIME` 주최자 열람 가능 시각은 `RealtimeParty.hostViewableAt()` 기준
- `now == hostViewableAt()`이면 열람 가능
- 공유 링크 토큰이나 URL은 응답에 포함하지 않음

공통 목록 item:

- 최신순 정렬
- 같은 `createdAt`이면 `id DESC` 정렬
- `rollingPaperId`, `position`, `writerNickname`, `content`, `wrapperImageUrl` 응답
- `position`은 최신순 전체 목록 기준으로 계산
- `content`는 상세 오버레이에서 별도 상세 조회 없이 표시할 수 있어야 함
- 이미지가 없으면 `wrapperImageUrl = null`
- 여러 이미지가 있으면 `sort_order ASC` 첫 번째 이미지 사용
- 래퍼 이미지는 `wrapper_id IN (...)`으로 bulk 조회해 N+1을 방지

주최자용 상세:

- 특정 롤링페이퍼 ID로 상세 조회 성공
- 상세 조회는 목록 기반 상세 오버레이의 기본 플로우가 아니라 보조 플로우임
- 해당 파티의 롤링페이퍼가 아니면 404
- 소유자가 아니면 403
- 열람 가능 전이면 403 `ROLLING_PAPER_NOT_VIEWABLE`

페이지네이션:

- totalCount 0이면 `totalPages = 0`, `items = []`
- totalCount 7이면 1페이지 7개
- totalCount 8이면 1페이지 7개, 2페이지 1개
- totalCount 14이면 1페이지 7개, 2페이지 7개
- totalCount 15이면 1페이지 7개, 2페이지 7개, 3페이지 1개
- `page`가 1보다 작으면 1페이지로 보정
- 존재하지 않는 페이지 요청 시 `page`는 요청값 그대로, `items = []`, `hasNext = false`

시간:

- `liveEndAt`, `partyEndAt`은 KST 기준 `LocalDateTime` 문자열로 응답
- `partyEndAt`은 `Party.endedAt()` 기준
- 동일 요청 내 열람 검증과 응답 계산은 같은 `now` 값을 사용

---

## 10. 미확정 사항

1. 참가자용 파티 자체 종료 후 조회 허용 정책은 현재 논의안이다. 기획 변경 시 종료 후 조회를 막을 수 있다.
