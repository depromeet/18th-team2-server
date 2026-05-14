# Archive Party Detail & My Rolling Paper API 설계

- 작성일: 2026-05-14
- 대상: `com.team2.server.party` (Kotlin + Spring Boot)
- 관련 spec: [`2026-05-07-archive-list-api-design.md`](2026-05-07-archive-list-api-design.md)
- 목적: 보관함 화면에서 한 파티 항목 클릭 시 파티 상세를 보여주고, "내가 남긴 롤링페이퍼 보기" 모달용 데이터를 제공
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 0. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| API 분리 | Detail / my-rolling-paper 두 API |
| Path 식별자 | `partyId` |
| 권한 | 로그인 필수 + 해당 파티 `participant` 본인만 |
| 응답 DTO 구조 | 통합 DTO + 섹션별 nesting(participants/rollingPaper/chat). PAPER_ONLY는 participants/chat = `null` |
| 헤더 이미지 정책 | "첫 롤페 wrapper, 없으면 fallback wrapper id=1" 이미지 |
| 참여자 명단 | `RealtimeParticipantProfile.nickname` 전체 + `totalCount` |
| 참여자 totalCount 정의 | `RealtimeParticipantProfile` count (입장해서 닉네임이 정해진 사람) |
| 채팅 메시지 | 최근 50개 `createdAt DESC` + `totalCount` + `hasMore` |
| 내 롤페 노출 | detail은 `hasMyRollingPaper: Boolean`. 모달 진입 시 별도 API |
| 패키지 | `party/` feature 내 4-레이어 (`api/`, `application/usecase/`). archive-list spec과 동일 위치 |
| 신규 ErrorCode | 없음. 기존 재사용 |

---

## 1. API 스펙

응답 예시의 JSON은 `ApiResponse.data` payload 기준이며, 실제 HTTP 응답은 기존 컨트롤러 관례대로 공통 `ApiResponse`로 감싼다.

### 1-1. 보관함 파티 상세 조회

```http
GET /api/v1/archives/parties/{partyId}
Authorization: Bearer <jwt>          # 필수
```

#### 응답 (200) — REALTIME, 참가자

```json
{
  "partyId": 1024,
  "name": "김유빈의 파티",
  "celebrantNickname": "김유빈",
  "partyOption": "REALTIME",
  "isHost": false,
  "startedAt": "2026-11-25T14:00:00",
  "endedAt": "2026-12-02T14:00:00",
  "headerImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg",
  "participants": {
    "totalCount": 13,
    "nicknames": ["해파리", "해파리", "..."]
  },
  "rollingPaper": {
    "totalCount": 17,
    "hasMyRollingPaper": true
  },
  "chat": {
    "totalCount": 124,
    "hasMore": true,
    "messages": [
      {
        "id": 1001,
        "writerNickname": "해파리",
        "content": "생추카!",
        "createdAt": "2026-11-25T14:01:00"
      }
    ]
  }
}
```

#### 응답 (200) — REALTIME, 주최자

```json
{
  "partyId": 1024,
  "name": "김유빈의 파티",
  "celebrantNickname": "김유빈",
  "partyOption": "REALTIME",
  "isHost": true,
  "startedAt": "2026-11-25T14:00:00",
  "endedAt": "2026-12-02T14:00:00",
  "headerImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg",
  "participants": {
    "totalCount": 13,
    "nicknames": ["해파리", "해파리", "..."]
  },
  "rollingPaper": {
    "totalCount": 17,
    "hasMyRollingPaper": false
  },
  "chat": {
    "totalCount": 124,
    "hasMore": true,
    "messages": [
      {
        "id": 1001,
        "writerNickname": "해파리",
        "content": "생추카!",
        "createdAt": "2026-11-25T14:01:00"
      }
    ]
  }
}
```

#### 응답 (200) — PAPER_ONLY

```json
{
  "partyId": 2048,
  "name": "김유빈의 롤링페이퍼",
  "celebrantNickname": "김유빈",
  "partyOption": "PAPER_ONLY",
  "isHost": false,
  "startedAt": "2026-11-25T00:00:00",
  "endedAt": "2026-12-02T00:00:00",
  "headerImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg",
  "participants": null,
  "rollingPaper": {
    "totalCount": 0,
    "hasMyRollingPaper": false
  },
  "chat": null
}
```

#### 필드 매핑

| 필드 | 타입 | 매핑 |
|---|---|---|
| `partyId` | `Long` | `Party.id` |
| `name` | `String` (nullable 허용 안 함) | `Party.name`, null이면 `""` |
| `celebrantNickname` | `String?` | `Party.celebrantNickname` |
| `partyOption` | enum | `Party.partyOption` (`REALTIME` / `PAPER_ONLY`) |
| `isHost` | `Boolean` | `party.ownerId == principal.userId` |
| `startedAt` | `LocalDateTime` (KST) | `Party.startedAt` |
| `endedAt` | `LocalDateTime` (KST) | `Party.endedAt()` (= `startedAt + 7일`) |
| `headerImageUrl` | `String?` | "헤더 이미지 정책" 참고 |
| `participants` | object \| null | `REALTIME`이면 non-null, `PAPER_ONLY`이면 `null` |
| `participants.totalCount` | `Long` | `RealtimeParticipantProfile` count by `party_id` |
| `participants.nicknames` | `List<String>` | `RealtimeParticipantProfile.nickname` 전체, `id ASC` |
| `rollingPaper.totalCount` | `Long` | `RollingPaper` count by `party_id` |
| `rollingPaper.hasMyRollingPaper` | `Boolean` | 내 `Participant.id`로 `RollingPaper` exists 여부 |
| `chat` | object \| null | `REALTIME`이면 non-null, `PAPER_ONLY`이면 `null` |
| `chat.totalCount` | `Long` | `ChatMessage` count by `party_id` |
| `chat.hasMore` | `Boolean` | `chat.totalCount > chat.messages.size` |
| `chat.messages` | `List<ChatMessageItem>` | `createdAt DESC, id DESC` 최근 `CHAT_RECENT_LIMIT`개 |
| `chat.messages[].id` | `Long` | `ChatMessage.id` |
| `chat.messages[].writerNickname` | `String` | `ChatMessage.participant`의 `RealtimeParticipantProfile.nickname`. 비정상적으로 profile이 없으면 `""` |
| `chat.messages[].content` | `String` | `ChatMessage.content` |
| `chat.messages[].createdAt` | `LocalDateTime` (KST) | `ChatMessage.createdAt` |

#### 헤더 이미지 정책 (`resolveHeaderImageUrl(party)`)

```text
1. firstPaper = RollingPaper의 party_id 기준 첫 번째 (createdAt ASC, id ASC)
2. wrapperId = firstPaper?.wrapper.id ?: FALLBACK_WRAPPER_ID  // = 1
3. imageUrl = ImageQueryService.findFirstImageUrl(ROLLING_PAPER_WRAPPER, wrapperId)
4. imageUrl이 null이면 응답의 headerImageUrl도 null
```

이유:

- 화면 헤더는 wrapper 이미지를 재사용한다는 결정.
- 첫 롤페가 있으면 그 wrapper를 노출 → 자연스럽고 결정론적.
- 첫 롤페가 없는 파티(0개)에서도 보관함 list에는 항목이 떠 있을 수 있다 → fallback wrapper id=1로 항상 헤더가 비지 않도록 보장.
- fallback wrapper에 등록된 이미지가 없으면 응답 `headerImageUrl`은 `null`이 되고 프론트가 자체 정적 자원으로 처리한다.

#### 권한 / 인증

- Authorization 헤더 필수. 없거나 invalid Bearer token이면 `401 AUTH_*`.
- `Party`가 없으면 `404 PARTY_NOT_FOUND`.
- 로그인 회원의 `Participant`가 해당 파티에 없으면 `403 PARTY_FORBIDDEN`.
  - 주의: 주최자도 파티 생성 시 자동으로 `Participant`가 만들어진다(`GetUpcomingPartiesUseCase` 분석 결과). 따라서 host/참가자 모두 `Participant` 단일 검증으로 커버.

### 1-2. 내가 남긴 롤링페이퍼 단건 조회

```http
GET /api/v1/archives/parties/{partyId}/my-rolling-paper
Authorization: Bearer <jwt>          # 필수
```

#### 응답 (200)

```json
{
  "rollingPaperId": 30,
  "writerNickname": "해파리",
  "content": "생일 축하해!!! 이 글자의 최대 길이는 여기까지...",
  "wrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg",
  "createdAt": "2026-11-25T14:05:00"
}
```

#### 필드 매핑

| 필드 | 타입 | 매핑 |
|---|---|---|
| `rollingPaperId` | `Long` | `RollingPaper.id` |
| `writerNickname` | `String` | `RollingPaper.writerNickname` 스냅샷 |
| `content` | `String` | `RollingPaper.content` |
| `wrapperImageUrl` | `String?` | wrapper의 `ImageTargetType.ROLLING_PAPER_WRAPPER` 첫 이미지 |
| `createdAt` | `LocalDateTime` (KST) | `RollingPaper.createdAt` |

#### 권한 / 단건성

- Authorization 필수. 없으면 `401`.
- `Party`가 없으면 `404 PARTY_NOT_FOUND`.
- 내 `Participant`가 해당 파티에 없으면 `403 PARTY_FORBIDDEN`.
- 내 `Participant.id` 기준 `RollingPaper`가 없으면 `404 ROLLING_PAPER_NOT_FOUND`.
- `RollingPaper`의 `uk_rolling_paper_writer_participant` 제약상 한 participant당 최대 1개이므로 단수형 응답이 자연스럽다.

---

## 2. 에러 응답

| 상황 | HTTP | ErrorCode | 비고 |
|---|---|---|---|
| Authorization 누락 | 401 | 기존 정책 | 컨트롤러 진입 전 필터 단계 |
| invalid Bearer token | 401 | `AUTH_INVALID_TOKEN` | 기존 정책 |
| 없는 `partyId` | 404 | `PARTY_NOT_FOUND` | 두 API 공통, 먼저 검증 |
| 내 `Participant`가 없음 | 403 | `PARTY_FORBIDDEN` | 두 API 공통 |
| my-rolling-paper, 내 롤페 없음 | 404 | `ROLLING_PAPER_NOT_FOUND` | my-rolling-paper만 |

신규 ErrorCode는 추가하지 않는다. 두 API 모두 `partyId` 검증을 `participant` 검증보다 먼저 수행해 404가 403보다 우선되게 한다.

---

## 3. 패키지·파일 구조

archive-list spec과 같은 위치인 `party/` feature 내 4-레이어로 신규 추가한다. 기존 `controller/`, `service/`, `usecase/`, `repository/`, `dto/`는 건드리지 않고 일부 Repository 메서드만 추가한다 (마이그레이션 PR이 통합 시 함께 이동).

```text
party/
├── api/
│   ├── ArchiveDetailApi.kt
│   ├── ArchiveDetailController.kt
│   └── dto/
│       ├── ArchiveDetailResponse.kt
│       ├── ArchiveParticipantsResponse.kt
│       ├── ArchiveRollingPaperResponse.kt
│       ├── ArchiveChatResponse.kt
│       ├── ArchiveChatMessageResponse.kt
│       └── ArchiveMyRollingPaperResponse.kt
└── application/
    └── usecase/
        ├── GetArchivedPartyDetailUseCase.kt
        └── GetMyRollingPaperUseCase.kt
```

archive-list가 추가하는 `ArchiveApi.kt`, `ArchiveController.kt`, `GetMyArchiveUseCase.kt`와 충돌하지 않는 새 파일명이다.

### 의존 방향

```text
api → application/usecase → 기존 repository / entity
                          → common/service/ImageQueryService
```

- Controller는 Repository를 직접 호출하지 않는다.
- UseCase가 Repository와 `ImageQueryService`를 주입받는다.
- UseCase는 다른 feature의 Service에 의존하지 않는다 (chat 도메인의 Repository만 의존).
- UseCase는 `HttpServletRequest`, `AuthenticationPrincipal`, `ResponseEntity`를 모른다.

---

## 4. UseCase 흐름

### 4-1. `GetArchivedPartyDetailUseCase`

```kotlin
@Service
class GetArchivedPartyDetailUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val imageQueryService: ImageQueryService,
) {
    @Transactional(readOnly = true)
    fun invoke(partyId: Long, userId: Long): ArchiveDetailResponse
}
```

흐름:

1. `party = partyRepository.findByIdOrNull(partyId) ?: throw BusinessException(PARTY_NOT_FOUND)`
2. `myParticipant = participantRepository.findByPartyIdAndUserId(partyId, userId) ?: throw BusinessException(PARTY_FORBIDDEN)`
3. `isHost = (party.ownerId == userId)`
4. `headerImageUrl = resolveHeaderImageUrl(party)`
5. `rollingPaperTotal = rollingPaperRepository.countByPartyId(partyId)`
6. `hasMyRollingPaper = rollingPaperRepository.existsByWriterId(myParticipant.id)`
7. `partyOption == REALTIME`인 경우만:
   - `profiles = realtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(partyId)`
   - `chatTotal = chatMessageRepository.countByPartyId(partyId)`
   - `chatMessages = chatMessageRepository.findRecentByPartyId(partyId, CHAT_RECENT_LIMIT)`
8. `ArchiveDetailResponse(...)` 빌드 후 반환

private helper:

```kotlin
private fun resolveHeaderImageUrl(party: Party): String? {
    val firstPaper = rollingPaperRepository
        .findFirstByPartyIdOrderByCreatedAtAscIdAsc(party.id)
    val wrapperId = firstPaper?.wrapper?.id ?: FALLBACK_WRAPPER_ID
    return imageQueryService.findFirstImageUrl(ImageTargetType.ROLLING_PAPER_WRAPPER, wrapperId)
}

companion object {
    const val CHAT_RECENT_LIMIT: Int = 50
    const val FALLBACK_WRAPPER_ID: Long = 1L
}
```

### 4-2. `GetMyRollingPaperUseCase`

```kotlin
@Service
class GetMyRollingPaperUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val imageQueryService: ImageQueryService,
) {
    @Transactional(readOnly = true)
    fun invoke(partyId: Long, userId: Long): ArchiveMyRollingPaperResponse
}
```

흐름:

1. `partyRepository.findByIdOrNull(partyId) ?: throw BusinessException(PARTY_NOT_FOUND)` (존재만 확인)
2. `participant = participantRepository.findByPartyIdAndUserId(partyId, userId) ?: throw BusinessException(PARTY_FORBIDDEN)`
3. `paper = rollingPaperRepository.findByWriterId(participant.id) ?: throw BusinessException(ROLLING_PAPER_NOT_FOUND)`
4. `wrapperImageUrl = imageQueryService.findFirstImageUrl(ROLLING_PAPER_WRAPPER, paper.wrapper.id)`
5. `ArchiveMyRollingPaperResponse(paper, wrapperImageUrl)` 반환

---

## 5. Repository 변경

기존 Repository에 메서드만 추가한다. 기존 시그니처는 변경하지 않는다.

### 5-1. `ParticipantRepository`

archive-list spec에서도 `findByPartyIdAndUserId`를 추가 예정이다. 메서드 중복이면 그대로 재사용.

```kotlin
fun findByPartyIdAndUserId(partyId: Long, userId: Long): Participant?
```

### 5-2. `RealtimeParticipantProfileRepository`

```kotlin
@Query("""
    select p from RealtimeParticipantProfile p
    join fetch p.participant pt
    where pt.party.id = :partyId
    order by p.id asc
""")
fun findAllByPartyIdOrderByIdAsc(partyId: Long): List<RealtimeParticipantProfile>
```

### 5-3. `RollingPaperRepository`

```kotlin
fun countByPartyId(partyId: Long): Long
fun existsByWriterId(writerId: Long): Boolean
fun findByWriterId(writerId: Long): RollingPaper?
fun findFirstByPartyIdOrderByCreatedAtAscIdAsc(partyId: Long): RollingPaper?
```

`writer`는 `Participant`이므로 Spring Data 메서드명 규약상 `findByWriterId(participantId)`는 `RollingPaper.writer.id`를 의미한다.

### 5-4. `ChatMessageRepository`

```kotlin
fun countByPartyId(partyId: Long): Long

@Query("""
    select cm
    from ChatMessage cm
    join fetch cm.participant p
    where cm.party.id = :partyId
    order by cm.createdAt desc, cm.id desc
""")
fun findRecentByPartyId(partyId: Long, pageable: Pageable): List<ChatMessage>
```

UseCase에서 `PageRequest.of(0, CHAT_RECENT_LIMIT)`로 호출한다. `RealtimeParticipantProfile.nickname`은 별도 쿼리로 batch 조회한다:

```kotlin
// realtimeParticipantProfileRepository에 추가
fun findAllByParticipantIdIn(participantIds: Collection<Long>): List<RealtimeParticipantProfile>
```

UseCase에서 `messages`의 `participant.id` 집합으로 한 번에 nickname을 모아 메모리 join.

---

## 6. Controller / Swagger

### 6-1. `ArchiveDetailController`

```kotlin
@RestController
@RequestMapping("/api/v1/archives")
class ArchiveDetailController(
    private val getArchivedPartyDetailUseCase: GetArchivedPartyDetailUseCase,
    private val getMyRollingPaperUseCase: GetMyRollingPaperUseCase,
) : ArchiveDetailApi {
    @GetMapping("/parties/{partyId}")
    override fun getArchivedPartyDetail(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchiveDetailResponse> =
        ApiResponse.success(getArchivedPartyDetailUseCase.invoke(partyId, principal.userId))

    @GetMapping("/parties/{partyId}/my-rolling-paper")
    override fun getMyRollingPaper(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchiveMyRollingPaperResponse> =
        ApiResponse.success(getMyRollingPaperUseCase.invoke(partyId, principal.userId))
}
```

- Controller는 Repository나 Service를 직접 호출하지 않는다.
- DTO 변환은 UseCase 내부에서 수행한다.

### 6-2. `ArchiveDetailApi` (Swagger)

문서화 응답:

- 200: `ApiResponse<ArchiveDetailResponse>` / `ApiResponse<ArchiveMyRollingPaperResponse>`
- 401, 403: `PARTY_FORBIDDEN`
- 404: `PARTY_NOT_FOUND`, my-rolling-paper의 `ROLLING_PAPER_NOT_FOUND`

`partyOption`은 enum value별 의미를 `@Schema`에 적는다.

### 6-3. SecurityConfig

별도 변경 없음. 두 엔드포인트는 기본 `anyRequest().authenticated()`에 의해 인증 회원만 접근 가능. `/api/v1/archives/**` permitAll을 추가하지 않는다 (archive-list도 별도 처리한다는 가정).

archive-list와의 정합성:

- archive-list spec은 비로그인 시 200 빈 응답을 허용한다.
- 하지만 detail/my-rolling-paper는 로그인 필수.
- archive-list 경로(`GET /api/v1/archive`)와 detail 경로(`GET /api/v1/archives/parties/{partyId}` 등)는 base path가 달라 별도 permitAll로 분리할 수 있다.
- list와 detail의 security 룰이 충돌하지 않도록 detail 경로는 permitAll에 포함하지 않는다.

---

## 7. 시간 정책

- KST 기준 `LocalDateTime` 문자열로 직렬화 (기존 API와 일치).
- `endedAt = Party.endedAt() = startedAt + Party.ENDED_AFTER_DAYS(7)`.
- 동일 요청 안에서는 `now` 값을 한 번만 계산해 사용 — 단, 이번 두 API는 현재 시각을 사용하는 분기가 없다.

---

## 8. 테스트 계획

### 8-1. Controller 통합 테스트

새 파일: `src/test/kotlin/com/team2/server/party/api/ArchiveDetailControllerTest.kt`

`@SpringBootTest` + `@Import(TestcontainersConfiguration::class)`.

#### 보관함 파티 상세

검증:

- REALTIME 참가자: 200, `isHost=false`, `participants` non-null, `chat` non-null, `rollingPaper.hasMyRollingPaper`가 작성 여부에 일치
- REALTIME 주최자: 200, `isHost=true`. 주최자 participant 자동 생성이라 권한 통과
- PAPER_ONLY: 200, `participants=null`, `chat=null`
- 비로그인: 401
- 다른 파티에만 속한 회원: 403 `PARTY_FORBIDDEN`
- 없는 `partyId`: 404 `PARTY_NOT_FOUND`
- 채팅 0개: `chat.totalCount=0`, `hasMore=false`, `messages=[]`
- 채팅 60개: `messages.length=50`, `hasMore=true`, `totalCount=60`, `createdAt DESC` 정렬
- 롤페 0개: `rollingPaper.totalCount=0`, `hasMyRollingPaper=false`, `headerImageUrl`이 fallback wrapper id=1의 이미지
- 롤페 N개, 내가 작성: `hasMyRollingPaper=true`
- 롤페 N개, 내가 미작성: `hasMyRollingPaper=false`
- 참여자 닉네임: `RealtimeParticipantProfile.nickname` 전체와 일치, `totalCount`도 같음
- `headerImageUrl`: 첫 롤페가 있으면 그 wrapper 이미지

#### my-rolling-paper

검증:

- 작성한 회원: 200, `rollingPaperId`, `content`, `wrapperImageUrl`, `writerNickname`, `createdAt`
- 미작성 회원: 404 `ROLLING_PAPER_NOT_FOUND`
- 다른 파티 회원: 403 `PARTY_FORBIDDEN`
- 비로그인: 401
- 없는 `partyId`: 404 `PARTY_NOT_FOUND` (participant 검증보다 우선)

### 8-2. UseCase 단위 테스트 (선택)

- `resolveHeaderImageUrl` fallback 분기: 첫 롤페 없는 경우 wrapper id=1 이미지 반환
- 첫 롤페 wrapper에 image가 없는 경우 `null` 반환

### 8-3. Security 테스트

기존 `SecurityIntegrationTest`에 케이스 추가:

- `GET /api/v1/archives/parties/{partyId}`: 토큰 없이 401
- `GET /api/v1/archives/parties/{partyId}/my-rolling-paper`: 토큰 없이 401
- invalid Bearer token: 401

### 8-4. 전체 테스트

```bash
./gradlew test
```

`TestcontainersConfiguration` 경유 검증 후 Docker 컨테이너 누수 확인:

```bash
docker ps -a --filter "label=org.testcontainers"
```

---

## 9. 구현 순서

승인 후 진행 순서:

1. `ArchiveDetailResponse`, `ArchiveParticipantsResponse`, `ArchiveRollingPaperResponse`, `ArchiveChatResponse`, `ArchiveChatMessageResponse`, `ArchiveMyRollingPaperResponse` DTO 추가
2. Repository 메서드 추가
   - `ParticipantRepository.findByPartyIdAndUserId(...)` (archive-list와 중복 시 그대로)
   - `RealtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(...)`, `findAllByParticipantIdIn(...)`
   - `RollingPaperRepository.countByPartyId(...)`, `existsByWriterId(...)`, `findByWriterId(...)`, `findFirstByPartyIdOrderByCreatedAtAscIdAsc(...)`
   - `ChatMessageRepository.countByPartyId(...)`, `findRecentByPartyId(...)`
3. `GetArchivedPartyDetailUseCase` 추가
4. `GetMyRollingPaperUseCase` 추가
5. `ArchiveDetailApi`, `ArchiveDetailController` 추가
6. `ArchiveDetailControllerTest` 추가
7. `SecurityIntegrationTest` 케이스 추가
8. 검증 실행
   - `./gradlew test --tests com.team2.server.party.api.ArchiveDetailControllerTest`
   - 필요 시 `./gradlew test`

---

## 10. 미확정 / 가정

1. archive-list spec의 `ParticipantRepository.findByPartyIdAndUserId` 추가가 같은 PR에 들어오지 않으면 이번 PR에서 직접 추가한다. 시그니처 충돌은 없다.
2. fallback wrapper id=1은 운영상 항상 존재한다는 전제다. seed/Flyway에 wrapper id=1 보장이 없다면 별도 작업이 필요하지만 이번 PR 범위 밖이다.
3. `RealtimeParticipantProfile`의 닉네임은 변경 가능성이 낮다고 보고 join 방식으로 처리한다. 향후 nickname 변경 정책이 도입되면 `ChatMessage`에 `writerNicknameSnapshot` 컬럼 도입을 별도 과제로 다룬다.
4. `headerImageUrl`이 `null`(첫 롤페 없음 + fallback wrapper도 이미지 없음)인 케이스는 프론트가 자체 정적 자원으로 처리한다.
5. 보관함 list에 등장하지만 본인 `Participant`가 끊긴 비정상 케이스는 없다는 전제 (list가 `Participant` 기반이므로 일관). 만약 발생 시 detail은 `PARTY_FORBIDDEN`을 반환한다.
6. PAPER_ONLY 주최자 응답은 PAPER_ONLY 참가자 응답에서 `isHost=true`만 다르고 나머지 구조는 동일하다. 예시는 생략한다.
7. 채팅을 보낸 `Participant`는 입장한 회원이므로 정상적으로 `RealtimeParticipantProfile`이 있다. 만약 데이터 정합 문제로 profile이 없으면 `writerNickname`을 `""`로 직렬화하며, 별도 에러로 처리하지 않는다.
