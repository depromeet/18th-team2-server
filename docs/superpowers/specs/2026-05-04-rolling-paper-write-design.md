# Rolling Paper Write and Wrapper Lookup API Design

- 작성일: 2026-05-04
- 기준 브랜치: `develop`
- 목적: 초대 링크 진입 후 롤링페이퍼 작성, 래퍼 선택지 조회 API 계약 확정
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 1. 결정 요약

래퍼는 별도 조회 API로 내려주고, 작성 API는 `wrapperId`를 받는다.

이유:

- 작성 요청에서 래퍼 이름이나 이미지 URL을 받으면 서버가 실제 선택 가능한 래퍼인지 검증하기 어렵다.
- 이미지 URL은 공통 `image` 테이블 기준으로 바뀔 수 있으므로, 클라이언트는 ID를 선택값으로 들고 있는 편이 안정적이다.
- 기존 캐릭터 조회 API도 `characterId`와 기본/썸네일 이미지 URL을 함께 내려주고, 이후 요청은 ID를 받는 구조다.

작성 성공 응답은 `201`과 함께 공통 `ApiResponse` 래퍼 안에 생성된 롤링페이퍼 ID만 내려준다.

이유:

- 작성 직후 다음 화면이 "받은 롤링페이퍼 리스트"라면 프론트는 어차피 리스트 조회를 다시 호출할 수 있다.
- 카드 표시용 `writerNickname`, `wrapperImageUrl`은 리스트 조회 응답의 책임으로 둔다.
- 작성 API는 생성 성공 여부와 생성 ID만 책임진다.

내부 엔티티와 DB 컬럼명도 래퍼 기준으로 정리한다.

이유:

- 외부 API는 `wrapperId`를 쓰는데 내부 FK가 `theme_id`이면 래핑지 개념과 테마 개념이 섞인다.
- 아직 운영 DB에 반영된 적이 없으므로 `theme`/`theme_id`를 유지할 필요가 낮다.
- `RollingPaper.wrapper`와 `wrapper_id`로 맞춰두면 프론트 계약, 백엔드 코드, DB 컬럼을 같은 용어로 설명할 수 있다.

---

## 2. 사용자 흐름

1. 사용자가 공유 링크로 진입한다.
2. 프론트가 `GET /api/v1/party-invites/{inviteToken}`으로 초대장/파티 요약을 조회한다.
3. 사용자가 `"롤페 작성하기"`를 누른다.
4. 프론트가 `GET /api/v1/rolling-paper-wrappers`로 선택 가능한 래퍼 목록을 조회한다.
5. 사용자가 닉네임, 내용, 래퍼를 모두 입력/선택한다.
6. 프론트가 `POST /api/v1/party-invites/{inviteToken}/rolling-papers`로 작성한다.
7. 작성 성공 후 프론트는 해당 파티의 받은 롤링페이퍼 리스트 화면으로 이동한다.

핵심 경계:

- 초대장 조회는 participant를 만들지 않는다.
- 롤링페이퍼 작성은 participant 생성/복원과 `hasWrittenPaper = true` 갱신을 책임진다.
- 래퍼 조회는 공개 조회다.
- 작성자 닉네임은 실시간 프로필 닉네임과 별개인 롤링페이퍼 작성 당시 스냅샷이다.
- 비회원은 별도 브라우저 식별자를 두지 않으므로, 서버는 닉네임 중복만 막는다.
- 주최자/주인공 participant도 롤링페이퍼를 작성할 수 있다. `isCelebrant = true`라는 이유로 작성 제한을 두지 않는다.

---

## 3. 입력 정책

### 3-1. 작성자 닉네임

- 필수
- blank이면 실패
- 최대 10자
- 한국어, 영어, 숫자, 특수문자를 모두 허용한다.
- 정규식으로 문자 종류를 제한하지 않는다.
- 파티 기준으로 롤링페이퍼 작성자 닉네임은 중복 불가
- 중복 기준은 `rolling_paper.party_id + rolling_paper.writer_nickname_key`

저장 정책:

- 요청 DTO는 `@NotBlank`, `@Size(max = 10)` 기준으로 검증한다.
- 서버는 앞뒤 공백을 제거한 닉네임을 `writer_nickname`에 저장한다.
- `writer_nickname_key`는 파티 식별자를 포함하지 않는 닉네임 정규화 값이며, `writer_nickname.trim().lowercase(Locale.ROOT)`로 생성한다.
- 내부 공백은 유지한다.
- 작성 후 실시간 프로필 닉네임이 바뀌어도 롤링페이퍼 리스트에는 `writer_nickname` 스냅샷을 사용한다.
- 중복 검증과 DB unique constraint는 `party_id`와 `writer_nickname_key` 조합 기준으로 동작한다.
- trim 이후 길이가 10자 이하여도, trim 전 요청 문자열이 10자를 초과하면 `@Size(max = 10)` 검증 실패로 처리한다.
- 닉네임 대소문자는 `writer_nickname_key` 생성 시 소문자로 정규화하므로 같은 값으로 취급한다. 예를 들어 같은 파티의 `abc`와 `ABC`는 중복이다.

### 3-2. 내용

- 필수
- blank이면 실패
- 최대 100자
- 한국어, 영어, 숫자, 특수문자를 모두 허용한다.
- 정규식으로 문자 종류를 제한하지 않는다.

저장 정책:

- 요청 DTO는 `@NotBlank`, `@Size(max = 100)` 기준으로 검증한다.
- 서버는 앞뒤 공백을 제거한 내용을 `content`에 저장한다.
- trim 이후 길이가 100자 이하여도, trim 전 요청 문자열이 100자를 초과하면 `@Size(max = 100)` 검증 실패로 처리한다.

### 3-3. 래퍼

- 필수
- 작성 요청은 `wrapperId`를 받는다.
- 존재하지 않는 `wrapperId`이면 실패
- 래퍼 이미지 URL은 `image.target_type = ROLLING_PAPER_WRAPPER`, `image.target_id = rolling_paper_wrapper.id`, `sort_order ASC` 첫 번째 이미지를 사용한다.

---

## 4. API 계약

### 4-1. 래퍼 목록 조회

```http
GET /api/v1/rolling-paper-wrappers
```

인증:

- `permitAll`
- Authorization header가 없어도 조회 가능
- 잘못된 Bearer token은 기존 정책대로 401

응답:

```json
{
  "status": 200,
  "data": [
    {
      "wrapperId": 1,
      "name": "기본 래퍼",
      "wrapperImageUrl": "/images/rolling-paper-wrappers/wrapper1.png"
    }
  ]
}
```

필드:

| 필드 | 설명 |
|---|---|
| `wrapperId` | 작성 요청의 `wrapperId`로 전달할 래퍼 ID |
| `name` | 래퍼 이름 |
| `wrapperImageUrl` | 래퍼 이미지 URL. 이미지가 없으면 `null` |

정렬:

- `rolling_paper_wrapper.id ASC`

조회 구현:

- 래퍼 목록은 `RollingPaperWrapperRepository.findAll(Sort.by(ASC, "id"))`로 조회한다.
- 래퍼 이미지 URL은 래퍼마다 개별 조회하지 않는다.
- `ImageTargetType.ROLLING_PAPER_WRAPPER`와 wrapper id 목록으로 image를 한 번에 조회한 뒤, 메모리에서 `targetId` 기준으로 매핑한다.
- 같은 래퍼에 이미지가 여러 개 있으면 `sortOrder ASC` 첫 번째 이미지를 사용한다.

### 4-2. 롤링페이퍼 작성

```http
POST /api/v1/party-invites/{inviteToken}/rolling-papers
```

인증:

- `permitAll`
- Authorization header가 없어도 작성 가능
- Authorization header가 유효하면 회원 participant를 생성/복원해서 작성한다.
- Authorization header가 없으면 비회원 participant를 생성해서 작성한다.
- 잘못된 Bearer token은 기존 정책대로 401

요청:

```json
{
  "writerNickname": "축하요정",
  "content": "생일 축하해!",
  "wrapperId": 1
}
```

응답:

```http
HTTP/1.1 201 Created
```

```json
{
  "status": 201,
  "data": {
    "rollingPaperId": 10
  }
}
```

응답 필드:

| 필드 | 설명 |
|---|---|
| `rollingPaperId` | 생성된 롤링페이퍼 ID |

처리 흐름:

컨트롤러 진입 시점:

1. 요청 DTO validation을 수행한다.
2. `writerNickname`, `content`는 `@NotBlank`, `@Size`로 검증한다.

UseCase 진입 이후:

1. `inviteToken`으로 `PartyInvite`를 조회한다.
2. 토큰이 없으면 `PARTY_NOT_FOUND`.
3. 초대 토큰이 만료됐으면 `INVITE_LINK_EXPIRED`.
4. 파티 생성 시각 기준 7일이 지나 파티가 종료됐으면 `PARTY_ENDED`.
5. `wrapperId`로 `RollingPaperWrapper`를 조회한다.
6. 래퍼가 없으면 `ROLLING_PAPER_WRAPPER_NOT_FOUND`.
7. 회원이면 파티 내 회원 participant를 조회하거나 생성한다.
8. 비회원이면 새 participant를 생성한다.
9. 회원 participant가 이미 `hasWrittenPaper = true`이면 `ROLLING_PAPER_ALREADY_WRITTEN`.
10. `writerNickname`, `content`를 trim한다.
11. 같은 파티에 같은 `writer_nickname_key` 롤링페이퍼가 있으면 `ROLLING_PAPER_NICKNAME_DUPLICATED`.
12. `RollingPaper`를 저장한다.
13. participant의 `hasWrittenPaper`를 `true`로 갱신한다.
14. 생성된 롤링페이퍼 ID를 반환한다.

트랜잭션 경계:

- UseCase의 작성 메서드 전체에 `@Transactional`을 둔다.
- `PartyInvite` 조회, 만료/종료 검증, `RollingPaperWrapper` 조회, participant 생성/복원, 롤링페이퍼 저장, `hasWrittenPaper = true` 갱신은 모두 같은 트랜잭션에서 처리한다.
- 중간 단계에서 실패하면 롤링페이퍼 저장과 participant 변경은 함께 롤백되어야 한다.

동시성:

- 애플리케이션 레벨 중복 체크만으로는 동시에 같은 닉네임을 제출하는 요청을 완전히 막을 수 없다.
- DB에 대소문자 무시 정규화 값인 `writer_nickname_key`를 저장하고 `(party_id, writer_nickname_key)` unique constraint를 둔다.
- unique constraint 위반은 `ROLLING_PAPER_NICKNAME_DUPLICATED`로 변환한다.
- 회원의 동시 이중 제출도 애플리케이션 레벨의 `hasWrittenPaper` 확인만으로는 완전히 막을 수 없다.
- DB에 `writer_participant_id` unique constraint를 둬서 같은 participant가 두 장 이상 작성하지 못하게 한다.
- `writer_participant_id` unique constraint 위반은 `ROLLING_PAPER_ALREADY_WRITTEN`으로 변환한다.
- `writer_participant_id` unique constraint는 같은 participant를 재사용하는 회원 작성자의 이중 제출을 막기 위한 제약이다.
- 비회원은 요청마다 새 participant를 만들기 때문에 이 제약으로 중복 작성이 막히지 않는다. 비회원 중복 작성 정책은 11번을 따른다.
- 회원 participant 생성/복원 중 `(party_id, user_id)` unique constraint가 발생하면 같은 트랜잭션에서 기존 participant를 다시 조회해 작성 흐름을 계속 진행한다.
- 재조회한 participant가 이미 `hasWrittenPaper = true`이면 `ROLLING_PAPER_ALREADY_WRITTEN`으로 응답한다.
- participant 재조회가 실패하면 원래 `DataIntegrityViolationException`을 다시 던진다.
- 롤링페이퍼 저장 시 unique constraint 위반은 UseCase에서 constraint 이름으로 분기해 `BusinessException`으로 변환한다.
- constraint 위반을 UseCase 안에서 변환할 수 있도록 `RollingPaper` 저장은 `saveAndFlush` 또는 명시적 flush로 처리한다.

## 5. 현재 엔티티 기준 변경점

현재 코드:

```kotlin
@Column(name = "writer_nickname", length = 20)
var writerNickname: String? = null

@Column(nullable = false, length = 1000)
var content: String
```

요구사항 반영 후:

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "wrapper_id", nullable = false)
var wrapper: RollingPaperWrapper

@Column(name = "writer_nickname", nullable = false, length = 10)
var writerNickname: String

@Column(name = "writer_nickname_key", nullable = false, length = 10)
var writerNicknameKey: String

@Column(nullable = false, length = 100)
var content: String
```

`theme` 필드와 `theme_id` 컬럼은 `wrapper` 필드와 `wrapper_id` 컬럼으로 변경한다.

`rolling_paper` 테이블 제약 추가:

```text
uk_rolling_paper_party_writer_nickname (party_id, writer_nickname_key)
uk_rolling_paper_writer_participant (writer_participant_id)
```

`writer_participant_id`는 현재 `RollingPaper.writer`의 `@ManyToOne Participant` 연관관계가 사용하는 FK 컬럼이다. 새 컬럼을 추가하는 것이 아니라, 기존 FK 컬럼에 unique constraint를 추가한다.

`participant` 테이블의 기존 제약:

```text
uk_participant_party_user (party_id, user_id)
```

회원 participant는 파티당 하나만 생기므로 `writer_participant_id` unique와 함께 회원의 중복 작성을 DB 레벨에서 막을 수 있다.

DB 기준:

- 현재 애플리케이션 기본 Hibernate dialect는 `MySQLDialect`이고, 테스트는 H2를 사용한다.
- 닉네임 중복 정책은 DB collation의 trailing-space 비교 방식에 기대지 않는다.
- 애플리케이션에서 trim한 값을 `writer_nickname`에 저장하고, 중복 사전 검증과 unique constraint는 `writer_nickname_key` 기준으로 처리한다.
- 대소문자 무시 중복 정책은 DB collation에만 기대지 않는다. 저장 전 중복 사전 검증도 `writer_nickname_key`로 수행한다.

`RollingPaperWrapper`는 현재 `name`만 가진다. 이미지는 공통 `image` 테이블에서 조회한다.

```text
image.target_type = ROLLING_PAPER_WRAPPER
image.target_id = rolling_paper_wrapper.id
```

운영 prod 설정은 DB schema `validate` 기준이므로, 엔티티 변경과 함께 운영 DB migration이 필요하다.
아직 prod에 롤링페이퍼 데이터가 올라간 적은 없으므로 기존 운영 데이터 backfill은 이번 범위에서 고려하지 않는다.

기본 래퍼 데이터:

- 캐릭터 기본 이미지와 같은 방식으로 `src/main/resources/static/images/rolling-paper-wrappers/` 아래 정적 이미지를 둔다.
- 기본 래퍼는 Flyway seed migration에서 `rolling_paper_wrapper`와 `image(target_type = ROLLING_PAPER_WRAPPER)`를 보장한다.
- 기본 래퍼 이미지 파일과 seed migration은 같은 배포 단위에 포함한다.
- 후속 래퍼 이미지 변경은 정적 파일 추가/교체 후 새 Flyway migration으로 `image` row를 추가하거나 갱신한다.
- 초기 migration은 빈 DB에서 한 번 실행되는 기준이므로 멱등 SQL을 사용하지 않는다. 재실행이 필요한 환경은 DB를 drop/recreate한 뒤 다시 migration을 적용한다.

후속 래퍼 이미지 추가 절차:

1. `src/main/resources/static/images/rolling-paper-wrappers/` 아래 이미지 파일을 추가한다.
2. 새 Flyway migration에서 `rolling_paper_wrapper` 또는 `image(target_type = ROLLING_PAPER_WRAPPER)` row를 추가/갱신한다.
3. 정적 이미지 URL 접근과 `GET /api/v1/rolling-paper-wrappers` 응답을 검증한다.
4. 실패 시 배포를 중단하고, 적용된 migration 상태에 맞춰 DB restore 또는 후속 보정 migration을 사용한다.

초대 토큰 만료 정책:

- `PartyInvite.expiresAt`은 파티 생성 시각 `party.createdAt + Party.ENDED_AFTER_DAYS`로 계산한다.
- `PAPER_ONLY`, `REALTIME` 모두 같은 파티 기간 7일 기준을 사용한다.
- 실시간 파티 입장 가능 시간은 `RealtimeParty.status()`와 별도 흐름에서 판단하고, 초대 토큰 자체의 만료 기준으로 사용하지 않는다.
- 초대장 조회 API는 기존 정책대로 만료된 토큰도 조회할 수 있다.
- 롤링페이퍼 작성 API는 만료된 토큰이면 `INVITE_LINK_EXPIRED`로 실패한다.

---

## 6. 추가할 코드 구조

현재 패키지 구조에 맞춰 최소 추가한다.

```text
rollingpaper/
├── controller/
│   ├── RollingPaperApi.kt
│   ├── RollingPaperController.kt
│   ├── RollingPaperWrapperApi.kt
│   └── RollingPaperWrapperController.kt
├── dto/
│   ├── CreateRollingPaperRequest.kt
│   ├── CreateRollingPaperResponse.kt
│   ├── RollingPaperWrapperResponse.kt
│   └── RollingPaperWrapperResult.kt
├── repository/
│   ├── RollingPaperRepository.kt
│   └── RollingPaperWrapperRepository.kt
├── service/
│   └── (기본 래퍼 seed는 Flyway migration에서 관리)
└── usecase/
    ├── CreateRollingPaperUseCase.kt
    └── GetRollingPaperWrappersUseCase.kt
```

의존 방향:

```text
controller -> usecase -> repository/entity/dto
```

지킬 것:

- Controller는 Repository를 직접 보지 않는다.
- 조회 UseCase에는 `@Transactional(readOnly = true)`를 둔다.
- 작성 UseCase에는 `@Transactional`을 둔다.
- 작성 UseCase는 `DataIntegrityViolationException`을 constraint 이름으로 분기한다.
- `uk_rolling_paper_party_writer_nickname` 위반은 `ROLLING_PAPER_NICKNAME_DUPLICATED`로 변환한다.
- `uk_rolling_paper_writer_participant` 위반은 `ROLLING_PAPER_ALREADY_WRITTEN`으로 변환한다.
- `uk_participant_party_user` 위반은 기존 participant 재조회로 복구하고, 복구할 수 없으면 원 예외를 다시 던진다.
- 이미지 URL 해석은 기존 `GetCharactersUseCase`처럼 `ImageRepository`와 `ImageTargetType`을 사용한다.
- 래퍼 목록 조회에서는 N+1을 피하기 위해 wrapper id 목록으로 image를 `IN` 조회한다.
- 현재 `Image`는 `targetType`, `targetId` 기반 공통 이미지 테이블이라 `RollingPaperWrapper`와 직접 JPA 연관관계가 없다. 이 구조에서는 fetch join보다 `ImageRepository`의 bulk 조회 메서드를 추가하는 방식이 더 단순하다.
- 작성 성공 응답은 생성 ID 전용 DTO를 사용한다.
- `RollingPaperWrapperResult`는 UseCase 내부 결과 DTO, `RollingPaperWrapperResponse`는 Controller 응답 DTO로 구분한다.

---

## 7. ErrorCode 추가안

```kotlin
ROLLING_PAPER_WRAPPER_NOT_FOUND(HttpStatus.NOT_FOUND, "롤링페이퍼 래퍼를 찾을 수 없습니다")
ROLLING_PAPER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 롤링페이퍼 닉네임입니다")
ROLLING_PAPER_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 롤링페이퍼를 작성했습니다")
```

기존 ErrorCode 재사용:

| 상황 | ErrorCode | HTTP status |
|---|---|---|
| 토큰 없음 | `PARTY_NOT_FOUND` | 404 |
| 초대 토큰 만료 | `INVITE_LINK_EXPIRED` | 400 |
| 파티 종료 | `PARTY_ENDED` | 400 |
| request validation 실패 | `VALIDATION_ERROR` | 400 |
| invalid Bearer token | `AUTH_INVALID_TOKEN` | 401 |

초대 토큰 만료는 기존 `INVITE_LINK_EXPIRED(HttpStatus.BAD_REQUEST)` 정책을 그대로 따른다. 이번 API에서 별도 `410 Gone`이나 `403 Forbidden`으로 바꾸지 않는다.

---

## 8. SecurityConfig

추가 공개 경로:

```kotlin
auth.requestMatchers(HttpMethod.GET, "/api/v1/rolling-paper-wrappers").permitAll()
auth.requestMatchers(HttpMethod.POST, "/api/v1/party-invites/*/rolling-papers").permitAll()
```

주의:

- `permitAll`이어도 JWT 필터의 invalid token 401 정책은 유지한다.
- `/api/v1/rolling-papers/**` 같은 넓은 wildcard 공개는 하지 않는다.

---

## 9. Swagger 문서화

래퍼 목록 조회:

- 200: 래퍼 목록 조회 성공
- 500: 공통 서버 오류

롤링페이퍼 작성:

- 201: 롤링페이퍼 작성 성공
- 400: validation 실패, 초대 링크 만료, 파티 종료
- 401: invalid token
- 404: 파티 없음, 래퍼 없음
- 409: 닉네임 중복, 이미 작성함
- 500: 공통 서버 오류

## 10. 테스트 계획

래퍼 목록 조회:

- 인증 없이 조회 성공
- `wrapperId`, `name`, `wrapperImageUrl` 응답
- 이미지가 없으면 `wrapperImageUrl = null`
- 이미지가 여러 개이면 `sort_order ASC` 첫 번째 이미지 URL 응답
- ID 오름차순 정렬

롤링페이퍼 작성:

- 인증 없이 작성 성공
- 인증 회원 작성 성공
- 주최자/주인공 participant도 작성 성공
- 작성 성공 시 `201`과 생성 ID 응답
- 닉네임 누락/blank 실패
- 닉네임 10자 초과 실패
- 내용 누락/blank 실패
- 내용 100자 초과 실패
- `wrapperId` 누락 실패
- 없는 `wrapperId` 실패
- 같은 파티 내 같은 닉네임 중복 실패
- trim 전후 동일한 닉네임 중복 실패
- 대소문자만 다른 닉네임도 중복으로 처리
- 다른 파티에서는 같은 닉네임 작성 가능
- 회원 participant가 이미 작성했으면 실패
- 같은 회원이 동시에 두 번 작성하면 한 건만 성공
- 작성 성공 시 participant `hasWrittenPaper = true`
- 만료된 초대 토큰으로 작성 실패
- 종료된 파티 작성 실패
- invalid Bearer token 401

동시성/DB 제약:

- 같은 파티에 같은 `writer_nickname_key`가 동시에 저장되면 unique constraint로 막힌다.
- constraint 위반은 `ROLLING_PAPER_NICKNAME_DUPLICATED`로 응답한다.
- 같은 `writer_participant_id`가 동시에 저장되면 unique constraint로 막힌다.
- constraint 위반은 `ROLLING_PAPER_ALREADY_WRITTEN`으로 응답한다.

---

## 11. 비회원 중복 작성 정책 (현행 결정)

- 이번 API는 비회원 브라우저 식별 토큰을 만들지 않는다.
- 비회원 작성 요청은 매번 새 participant를 생성한다.
- 따라서 비회원의 중복 작성 방지는 파티 내 닉네임 중복 제약으로만 처리한다.
- 같은 비회원이 다른 닉네임으로 여러 번 작성하는 것을 서버에서 막지 않는다.
- 이 정책을 바꾸려면 guest 식별 토큰 또는 브라우저 단위 식별 정책을 별도 설계해야 한다.

## 12. 문자 수 기준

- 문자 수 기준은 서버 validation의 `@Size` 기준을 따른다.
- 한국어, 영어, 숫자, 일반 특수문자를 허용하며 별도 문자 종류 제한은 두지 않는다.
