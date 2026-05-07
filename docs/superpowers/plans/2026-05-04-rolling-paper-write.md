# Rolling Paper Write and Wrapper Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 초대 토큰 기반 롤링페이퍼 작성 API와 래퍼 목록 조회 API를 추가한다. 작성 시 회원/비회원 participant를 생성 또는 복원하고, 작성자 닉네임 스냅샷과 래퍼 선택값을 저장한다.

**Architecture:** `rollingpaper/controller -> rollingpaper/usecase -> repository/entity` 구조를 따른다. 공개 API는 optional auth 정책을 사용한다. Authorization header가 없으면 익명으로 진행하고, 잘못된 Bearer token은 기존 JWT 필터 정책대로 401을 반환한다.

**Spec Reference:** `docs/superpowers/specs/2026-05-04-rolling-paper-write-design.md`

---

## Decisions

- 롤링페이퍼 작성 가능 시간은 `초대 토큰 유효 && Party.endedAt() 전`이다.
- `PartyInvite.expiresAt`은 `Party.endedAt()`으로 계산한다.
- 성공 응답은 기존 `ApiResponse` 래퍼를 유지한다.
- 기본 래퍼 이미지는 `src/main/resources/static/images/rolling-paper-wrappers/` 아래 정적 파일을 사용한다.
- `RollingPaper.theme` / `theme_id`는 `wrapper` / `wrapper_id`로 변경한다.
- 주최자/주인공 participant도 롤링페이퍼를 작성할 수 있다.
- 요청 DTO는 `@NotBlank`, `@Size` 기준으로 검증하고, 저장 전 `writerNickname`, `content`를 trim한다.
- 닉네임 대소문자는 같은 값으로 취급한다.

---

## File Structure

### 신규 생성

| 경로 | 책임 |
|---|---|
| `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperApi.kt` | 롤링페이퍼 작성 Swagger 계약 |
| `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperController.kt` | `POST /api/v1/party-invites/{inviteToken}/rolling-papers` |
| `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperApi.kt` | 래퍼 목록 조회 Swagger 계약 |
| `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperController.kt` | `GET /api/v1/rolling-paper-wrappers` |
| `src/main/kotlin/com/team2/server/rollingpaper/dto/CreateRollingPaperRequest.kt` | 작성 요청 DTO |
| `src/main/kotlin/com/team2/server/rollingpaper/dto/CreateRollingPaperResponse.kt` | 작성 응답 DTO |
| `src/main/kotlin/com/team2/server/rollingpaper/dto/RollingPaperWrapperResponse.kt` | 래퍼 목록 응답 DTO |
| `src/main/kotlin/com/team2/server/rollingpaper/dto/RollingPaperWrapperResult.kt` | 래퍼 목록 UseCase 결과 DTO |
| `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepository.kt` | 롤링페이퍼 저장/중복 조회 |
| `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperWrapperRepository.kt` | 래퍼 조회 |
| `src/main/kotlin/com/team2/server/rollingpaper/usecase/CreateRollingPaperUseCase.kt` | 작성 트랜잭션 |
| `src/main/kotlin/com/team2/server/rollingpaper/usecase/GetRollingPaperWrappersUseCase.kt` | 래퍼 목록 조회 |
| `src/test/kotlin/com/team2/server/rollingpaper/controller/RollingPaperControllerTest.kt` | 작성 API 통합 테스트 |
| `src/test/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperControllerTest.kt` | 래퍼 조회 API 통합 테스트 |

### 수정

| 경로 | 변경 내용 |
|---|---|
| `src/main/kotlin/com/team2/server/rollingpaper/entity/RollingPaper.kt` | `theme` -> `wrapper`, `theme_id` -> `wrapper_id`, 길이/nullable/unique 제약 |
| `src/main/kotlin/com/team2/server/common/entity/ImageTargetType.kt` | `ROLLING_PAPER_WRAPPER` 기존 값 재사용 확인 |
| `src/main/kotlin/com/team2/server/common/repository/ImageRepository.kt` | wrapper id 목록 기준 bulk image 조회 메서드 추가 |
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | 롤링페이퍼 래퍼 없음, 닉네임 중복, 이미 작성 에러 추가 |
| `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` | 래퍼 조회/롤링페이퍼 작성 공개 경로 추가 |
| `src/main/kotlin/com/team2/server/party/service/PartyInviteService.kt` | 초대 토큰 만료를 파티 종료 시각 기준으로 변경 |
| `src/test/kotlin/com/team2/server/party/service/PartyInviteServiceTest.kt` | 초대 토큰 만료 정책 테스트 갱신 |
| `src/test/kotlin/com/team2/server/party/repository/ParticipantRepositoryTest.kt` | `RollingPaper` 생성자 변경 반영 |
| `src/test/kotlin/com/team2/server/party/entity/PartyParticipationDomainTest.kt` | `RollingPaper` 생성자 변경 반영 |

### 정적 리소스

| 경로 | 변경 내용 |
|---|---|
| `src/main/resources/static/images/rolling-paper-wrappers/` | 기본 래퍼 이미지 파일 배치 |

---

## Task Order

## Task 1: 엔티티와 Repository 기반 정리

**Files:**
- Modify: `src/main/kotlin/com/team2/server/rollingpaper/entity/RollingPaper.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepository.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperWrapperRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/common/repository/ImageRepository.kt`

- [ ] `RollingPaper.theme`을 `wrapper`로, `theme_id`를 `wrapper_id`로 변경한다.
- [ ] `writerNickname`을 nullable false, length 10으로 변경한다.
- [ ] 대소문자 무시 중복 제약용 `writerNicknameKey`를 추가한다.
- [ ] `content` length를 100으로 변경한다.
- [ ] `rolling_paper`에 `(party_id, writer_nickname_key)`, `writer_participant_id` unique constraint를 추가한다.
- [ ] `RollingPaperRepository`에 `existsByPartyAndWriterNicknameKey(...)`와 `saveAndFlush(...)` 사용 경로를 준비한다.
- [ ] `RollingPaperWrapperRepository`를 추가한다.
- [ ] `ImageRepository`에 `findByTargetTypeAndTargetIdInOrderByTargetIdAscSortOrderAsc(...)` 형태의 bulk 조회 메서드를 추가한다.
- [ ] 기존 테스트의 `RollingPaper(theme = ...)` 생성자를 `wrapper = ...`로 갱신한다.

Run:

```bash
./gradlew test --tests com.team2.server.party.repository.ParticipantRepositoryTest --tests com.team2.server.party.entity.PartyParticipationDomainTest
```

## Task 2: 기본 래퍼 초기화

**Files:**
- Create: `src/main/resources/db/migration/V2__seed_default_assets.sql`
- Test: `src/test/kotlin/com/team2/server/db/FlywayMigrationTest.kt`

- [ ] Flyway seed migration에서 기본 래퍼 row를 보장한다.
- [ ] `image(target_type = ROLLING_PAPER_WRAPPER, target_id = wrapper.id, sort_order = 0)`를 보장한다.
- [ ] 이미지 URL은 `/images/rolling-paper-wrappers/...` 정적 경로를 사용한다.
- [ ] 런타임 initializer 없이 clean DB migration으로 기본 데이터가 생성되는지 검증한다.

Run:

```bash
./gradlew test --tests com.team2.server.db.FlywayMigrationTest
```

## Task 3: 래퍼 목록 조회 API

**Files:**
- Create: `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperApi.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperController.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/dto/RollingPaperWrapperResponse.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/dto/RollingPaperWrapperResult.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/usecase/GetRollingPaperWrappersUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/team2/server/rollingpaper/controller/RollingPaperWrapperControllerTest.kt`

- [ ] `GET /api/v1/rolling-paper-wrappers`를 추가한다.
- [ ] 응답은 `ApiResponse<List<RollingPaperWrapperResponse>>`로 반환한다.
- [ ] wrapper id 오름차순으로 조회한다.
- [ ] image는 wrapper id 목록으로 한 번에 조회하고, `sortOrder ASC` 첫 번째 URL을 매핑한다.
- [ ] 이미지가 없으면 `wrapperImageUrl = null`로 내려준다.
- [ ] 공개 경로를 `SecurityConfig`에 추가하고 invalid Bearer token 401 정책을 유지한다.
- [ ] Swagger 200/500 응답을 문서화한다.

Run:

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperWrapperControllerTest
```

## Task 4: 초대 토큰 만료 정책 변경

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/service/PartyInviteService.kt`
- Test: `src/test/kotlin/com/team2/server/party/service/PartyInviteServiceTest.kt`

- [ ] 새 초대 토큰의 `expiresAt`을 `party.endedAt()`으로 계산한다.
- [ ] `PAPER_ONLY`, `REALTIME` 모두 같은 7일 기준을 사용한다.
- [ ] 기존 유효 토큰 재사용 정책은 유지한다.
- [ ] 실시간 라이브 종료 시각은 초대 토큰 만료 기준으로 사용하지 않는다.

Run:

```bash
./gradlew test --tests com.team2.server.party.service.PartyInviteServiceTest
```

## Task 5: 롤링페이퍼 작성 UseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/rollingpaper/usecase/CreateRollingPaperUseCase.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/dto/CreateRollingPaperRequest.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/dto/CreateRollingPaperResponse.kt`
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`

- [ ] `ROLLING_PAPER_WRAPPER_NOT_FOUND`, `ROLLING_PAPER_NICKNAME_DUPLICATED`, `ROLLING_PAPER_ALREADY_WRITTEN`를 추가한다.
- [ ] 요청 DTO에 `@NotBlank`, `@Size(max = 10)`, `@Size(max = 100)`, `@field:Positive`를 적용한다.
- [ ] 초대 토큰 없음은 `PARTY_NOT_FOUND`로 처리한다.
- [ ] 초대 토큰 만료는 `INVITE_LINK_EXPIRED`로 처리한다.
- [ ] `party.endedAt()` 이후 작성은 `PARTY_ENDED`로 처리한다.
- [ ] 회원이면 기존 participant를 조회하고, 없으면 생성한다.
- [ ] 비회원이면 새 participant를 생성한다.
- [ ] `isCelebrant = true` participant도 작성 가능하게 둔다.
- [ ] `writerNickname`, `content`는 저장 전 trim한다.
- [ ] 같은 파티의 같은 닉네임은 대소문자 무시 사전 검증과 DB 정규화 키 unique constraint로 막는다.
- [ ] `RollingPaper`는 `saveAndFlush`로 저장해서 unique constraint 위반을 UseCase 안에서 변환한다.
- [ ] 작성 성공 시 participant의 `hasWrittenPaper`를 `true`로 갱신한다.
- [ ] `uk_participant_party_user` 충돌은 기존 participant 재조회로 복구한다.
- [ ] `uk_rolling_paper_party_writer_nickname` 충돌은 `ROLLING_PAPER_NICKNAME_DUPLICATED`로 변환한다.
- [ ] `uk_rolling_paper_writer_participant` 충돌은 `ROLLING_PAPER_ALREADY_WRITTEN`으로 변환한다.

Run:

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperControllerTest
```

## Task 6: 롤링페이퍼 작성 API

**Files:**
- Create: `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperApi.kt`
- Create: `src/main/kotlin/com/team2/server/rollingpaper/controller/RollingPaperController.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/team2/server/rollingpaper/controller/RollingPaperControllerTest.kt`

- [ ] `POST /api/v1/party-invites/{inviteToken}/rolling-papers`를 추가한다.
- [ ] `@AuthenticationPrincipal principal: UserPrincipal?`를 받아 optional auth로 처리한다.
- [ ] 성공 시 `@ResponseStatus(HttpStatus.CREATED)`와 `ApiResponse.success(HttpStatus.CREATED, response)`를 사용한다.
- [ ] 공개 경로를 method/path-specific으로 추가한다.
- [ ] Swagger 201/400/401/404/409/500 응답을 문서화한다.

Test cases:

- [ ] 인증 없이 작성 성공
- [ ] 인증 회원 작성 성공
- [ ] 주최자/주인공 작성 성공
- [ ] 닉네임 누락/blank/10자 초과 실패
- [ ] 내용 누락/blank/100자 초과 실패
- [ ] `wrapperId` 누락/없는 값 실패
- [ ] 같은 파티 내 같은 닉네임 실패
- [ ] trim 전후 같은 닉네임 실패
- [ ] 대소문자만 다른 닉네임 중복 실패
- [ ] 다른 파티에서는 같은 닉네임 작성 성공
- [ ] 회원 participant가 이미 작성했으면 실패
- [ ] 작성 성공 시 participant `hasWrittenPaper = true`
- [ ] 만료된 초대 토큰 작성 실패
- [ ] 시작 후 7일 지난 파티 작성 실패
- [ ] invalid Bearer token 401

Run:

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperControllerTest
```

## Task 7: 최종 검증

- [ ] 관련 테스트를 실행한다.

```bash
./gradlew test --tests com.team2.server.rollingpaper.controller.RollingPaperWrapperControllerTest --tests com.team2.server.rollingpaper.controller.RollingPaperControllerTest --tests com.team2.server.db.FlywayMigrationTest --tests com.team2.server.party.service.PartyInviteServiceTest
```

- [ ] 전체 테스트를 실행한다.

```bash
./gradlew test
```

- [ ] ktlint를 실행한다.

```bash
./gradlew ktlintCheck
```

- [ ] 최종 변경 파일을 확인한다.

```bash
git status --short
git diff --stat
```
