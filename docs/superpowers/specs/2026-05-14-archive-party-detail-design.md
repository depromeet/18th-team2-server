# Archive Party Detail API 설계

- 작성일: 2026-05-14
- 대상: `com.team2.server.party` (Kotlin + Spring Boot)
- 관련 spec: [`2026-05-07-archive-list-api-design.md`](2026-05-07-archive-list-api-design.md)
- 목적: 보관함 화면에서 한 파티 항목 클릭 시 파티 상세를 보여주고, "내가 남긴 롤링페이퍼 보기" 모달용 데이터를 같은 응답에 inline으로 제공
- 구현 전 확인 상태: 이 문서는 구현 전 설계 확인용이며, 승인 전에는 Kotlin 코드를 변경하지 않는다.

---

## 0. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| API | 단일 통합 API |
| URL | `GET /api/v1/archive/party/{partyId}` (단수형, 프론트 컨벤션) |
| 권한 | 로그인 필수 + 해당 파티 `participant` 본인만 |
| 응답 DTO | 평탄(flat) 구조. nesting 없음 |
| 내 롤페 표현 | `myPaperWritten` boolean + 3개 필드 (작성 안 했으면 모두 `null`) |
| PAPER_ONLY 분기 정보 | `partyOption` 필드로 명시 |
| 파티 종료 시각 | `partyEndedAt` 추가 (PAPER_ONLY 기간 표시용) |
| 참여자 명단 | `participants: { nickname }[]` 전체 (`RealtimeParticipantProfile` 기준) |
| 참여자 수 | `participantCount` = `RealtimeParticipantProfile` count |
| 채팅 메시지 | 최근 50개 `createdAt DESC` + `chatHasMore` boolean |
| 헤더 이미지 | 응답에 포함 안 함 (프론트 정적 자원) |
| 패키지 | `party/` feature 내 4-레이어 (`api/`, `application/usecase/`). archive-list spec과 동일 위치 |
| 신규 ErrorCode | 없음. 기존 재사용 |

---

## 1. API 스펙

응답 예시의 JSON은 `ApiResponse.data` payload 기준이며, 실제 HTTP 응답은 기존 컨트롤러 관례대로 공통 `ApiResponse`로 감싼다.

### 1-1. 보관함 파티 상세 조회

```http
GET /api/v1/archive/party/{partyId}
Authorization: Bearer <jwt>          # 필수
```

#### 응답 (200) — REALTIME, 참가자, 본인이 작성

```json
{
  "partyId": 1024,
  "partyName": "김유빈의 파티",
  "partyOption": "REALTIME",
  "role": "PARTICIPANT",
  "partyStartedAt": "2026-11-25T14:00:00",
  "partyEndedAt": "2026-12-02T14:00:00",
  "participantCount": 13,
  "paperCount": 17,
  "participants": [
    { "nickname": "해파리" },
    { "nickname": "해파리" }
  ],
  "chatMessages": [
    {
      "id": 1001,
      "authorName": "해파리",
      "content": "생추카!",
      "sentAt": "2026-11-25T14:01:00"
    }
  ],
  "chatHasMore": true,
  "myPaperWritten": true,
  "myPaperContent": "생일 축하해!!! 이 글자의 최대 길이는 여기까지...",
  "myPaperWriterNickname": "해파리",
  "myPaperWrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
}
```

#### 응답 (200) — REALTIME, 주최자

```json
{
  "partyId": 1024,
  "partyName": "김유빈의 파티",
  "partyOption": "REALTIME",
  "role": "HOST",
  "partyStartedAt": "2026-11-25T14:00:00",
  "partyEndedAt": "2026-12-02T14:00:00",
  "participantCount": 13,
  "paperCount": 17,
  "participants": [
    { "nickname": "해파리" }
  ],
  "chatMessages": [
    {
      "id": 1001,
      "authorName": "해파리",
      "content": "생추카!",
      "sentAt": "2026-11-25T14:01:00"
    }
  ],
  "chatHasMore": true,
  "myPaperWritten": false,
  "myPaperContent": null,
  "myPaperWriterNickname": null,
  "myPaperWrapperImageUrl": null
}
```

#### 응답 (200) — PAPER_ONLY, 참가자, 본인이 작성

```json
{
  "partyId": 2048,
  "partyName": "김유빈의 롤링페이퍼",
  "partyOption": "PAPER_ONLY",
  "role": "PARTICIPANT",
  "partyStartedAt": "2026-11-25T00:00:00",
  "partyEndedAt": "2026-12-02T00:00:00",
  "participantCount": 0,
  "paperCount": 5,
  "participants": [],
  "chatMessages": [],
  "chatHasMore": false,
  "myPaperWritten": true,
  "myPaperContent": "생축!",
  "myPaperWriterNickname": "해파리",
  "myPaperWrapperImageUrl": "/images/rolling-paper-wrappers/Topping_Candle.svg"
}
```

#### 필드 매핑

| 필드 | 타입 | 매핑 / 비고 |
|---|---|---|
| `partyId` | `Long` | `Party.id` |
| `partyName` | `String` | `Party.name`, null이면 `""` |
| `partyOption` | enum | `Party.partyOption` (`REALTIME` / `PAPER_ONLY`). PAPER_ONLY 화면 분기용 |
| `role` | enum | `"HOST"` if `party.ownerId == userId` else `"PARTICIPANT"` |
| `partyStartedAt` | `LocalDateTime` (KST) | `Party.startedAt` |
| `partyEndedAt` | `LocalDateTime` (KST) | `Party.endedAt()` (= `startedAt + 7일`) |
| `participantCount` | `Long` | `RealtimeParticipantProfile` count by `party_id`. PAPER_ONLY는 항상 `0` |
| `paperCount` | `Long` | `RollingPaper` count by `party_id` |
| `participants` | `List<{ nickname: String }>` | `RealtimeParticipantProfile.nickname` 전체, `id ASC`. PAPER_ONLY는 `[]` |
| `chatMessages` | `List<ChatMessageItem>` | `createdAt DESC, id DESC` 최근 `CHAT_RECENT_LIMIT(50)`개. PAPER_ONLY는 `[]` |
| `chatMessages[].id` | `Long` | `ChatMessage.id` |
| `chatMessages[].authorName` | `String` | `ChatMessage.participant`의 `RealtimeParticipantProfile.nickname`. 비정상적으로 profile이 없으면 `""` |
| `chatMessages[].content` | `String` | `ChatMessage.content` |
| `chatMessages[].sentAt` | `LocalDateTime` (KST) | `ChatMessage.createdAt` |
| `chatHasMore` | `Boolean` | `chatMessageRepository.countByPartyId(partyId) > chatMessages.size`. PAPER_ONLY는 `false` |
| `myPaperWritten` | `Boolean` | 내 `Participant.id`로 `RollingPaper` exists 여부 |
| `myPaperContent` | `String?` | `myPaperWritten=true`이면 `RollingPaper.content`, 아니면 `null` |
| `myPaperWriterNickname` | `String?` | `myPaperWritten=true`이면 `RollingPaper.writerNickname` (스냅샷), 아니면 `null` |
| `myPaperWrapperImageUrl` | `String?` | `myPaperWritten=true`이면 wrapper의 `ROLLING_PAPER_WRAPPER` 첫 이미지, 아니면 `null` |

PAPER_ONLY 정책:

- PAPER_ONLY 파티는 실시간 입장이 없으므로 `RealtimeParticipantProfile`이 생성되지 않는다 → `participants = []`, `participantCount = 0`.
- PAPER_ONLY 파티는 `ChatMessage`가 생성되지 않는다 → `chatMessages = []`, `chatHasMore = false`.
- PAPER_ONLY와 REALTIME 응답의 JSON shape은 동일하며(필드 누락 없음), 값으로만 분기한다.

#### 권한 / 인증

- Authorization 헤더 필수. 없거나 invalid Bearer token이면 `401 AUTH_*`.
- `Party`가 없으면 `404 PARTY_NOT_FOUND`.
- 로그인 회원의 `Participant`가 해당 파티에 없으면 `403 PARTY_FORBIDDEN`.
  - 주의: 주최자도 파티 생성 시 자동으로 `Participant`가 만들어진다. host/참가자 모두 `Participant` 단일 검증으로 커버.

---

## 2. 에러 응답

| 상황 | HTTP | ErrorCode | 비고 |
|---|---|---|---|
| Authorization 누락 | 401 | 기존 정책 | 컨트롤러 진입 전 필터 단계 |
| invalid Bearer token | 401 | `AUTH_INVALID_TOKEN` | 기존 정책 |
| 없는 `partyId` | 404 | `PARTY_NOT_FOUND` | participant 검증보다 우선 |
| 내 `Participant`가 없음 | 403 | `PARTY_FORBIDDEN` | 다른 파티에만 속한 회원 |

신규 ErrorCode는 추가하지 않는다. 내 롤페가 없는 경우는 별도 에러가 아니라 `myPaperWritten = false`로 응답한다.

---

## 3. 패키지·파일 구조

archive-list spec과 같은 위치인 `party/` feature 내 4-레이어로 신규 추가한다. 기존 `controller/`, `service/`, `usecase/`, `repository/`, `dto/`는 건드리지 않고 일부 Repository 메서드만 추가한다 (마이그레이션 PR이 통합 시 함께 이동).

```text
party/
├── api/
│   ├── ArchivePartyDetailApi.kt
│   ├── ArchivePartyDetailController.kt
│   └── dto/
│       ├── ArchivePartyDetailResponse.kt
│       ├── ArchiveParticipantResponse.kt
│       └── ArchiveChatMessageResponse.kt
└── application/
    └── usecase/
        └── GetArchivedPartyDetailUseCase.kt
```

archive-list가 추가하는 `ArchiveApi.kt`, `ArchiveController.kt`, `GetMyArchiveUseCase.kt`와 파일명 충돌하지 않는다.

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

### `GetArchivedPartyDetailUseCase`

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
    fun invoke(partyId: Long, userId: Long): ArchivePartyDetailResponse
}
```

흐름:

1. `party = partyRepository.findByIdOrNull(partyId) ?: throw BusinessException(PARTY_NOT_FOUND)`
2. `myParticipant = participantRepository.findByPartyIdAndUserId(partyId, userId) ?: throw BusinessException(PARTY_FORBIDDEN)`
3. `role = if (party.ownerId == userId) ArchiveRole.HOST else ArchiveRole.PARTICIPANT`
4. `paperCount = rollingPaperRepository.countByPartyId(partyId)`
5. `myPaper = rollingPaperRepository.findByWriterId(myParticipant.id)`
6. `myPaperWrapperImageUrl = myPaper?.let { imageQueryService.findFirstImageUrl(ROLLING_PAPER_WRAPPER, it.wrapper.id) }`
7. when (`party.partyOption`):
   - `REALTIME`:
     - `profiles = realtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(partyId)`
     - `chatTotal = chatMessageRepository.countByPartyId(partyId)`
     - `recentChat = chatMessageRepository.findRecentByPartyId(partyId, PageRequest.of(0, CHAT_RECENT_LIMIT))`
     - `chatProfileMap = realtimeParticipantProfileRepository.findAllByParticipantIdIn(recentChat.map { it.participant.id }).associateBy { it.participant.id }`
   - `PAPER_ONLY`:
     - `profiles = emptyList()`
     - `chatTotal = 0L`
     - `recentChat = emptyList()`
     - `chatProfileMap = emptyMap()`
8. `participants = profiles.map { ArchiveParticipantResponse(nickname = it.nickname) }`
9. `chatMessages = recentChat.map { msg ->
       ArchiveChatMessageResponse(
           id = msg.id,
           authorName = chatProfileMap[msg.participant.id]?.nickname ?: "",
           content = msg.content,
           sentAt = msg.createdAt,
       )
   }`
10. `chatHasMore = chatTotal > chatMessages.size`
11. `ArchivePartyDetailResponse(...)` 빌드 후 반환

상수:

```kotlin
companion object {
    const val CHAT_RECENT_LIMIT: Int = 50
}
```

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

fun findAllByParticipantIdIn(participantIds: Collection<Long>): List<RealtimeParticipantProfile>
```

`findAllByParticipantIdIn`은 채팅 메시지의 `authorName` 매핑용 batch 조회다.

### 5-3. `RollingPaperRepository`

```kotlin
fun countByPartyId(partyId: Long): Long
fun findByWriterId(writerId: Long): RollingPaper?
```

`writer`는 `Participant`이므로 Spring Data 메서드명 규약상 `findByWriterId(participantId)`는 `RollingPaper.writer.id`를 의미한다.

### 5-4. `ChatMessageRepository`

```kotlin
fun countByPartyId(partyId: Long): Long

@Query("""
    select cm
    from ChatMessage cm
    join fetch cm.participant
    where cm.party.id = :partyId
    order by cm.createdAt desc, cm.id desc
""")
fun findRecentByPartyId(partyId: Long, pageable: Pageable): List<ChatMessage>
```

UseCase에서 `PageRequest.of(0, CHAT_RECENT_LIMIT)`로 호출한다.

### 5-5. `ImageQueryService`

이미 존재. `findFirstImageUrl(ImageTargetType.ROLLING_PAPER_WRAPPER, wrapperId)` 그대로 활용한다.

---

## 6. Controller / Swagger

### 6-1. `ArchivePartyDetailController`

```kotlin
@RestController
@RequestMapping("/api/v1/archive")
class ArchivePartyDetailController(
    private val getArchivedPartyDetailUseCase: GetArchivedPartyDetailUseCase,
) : ArchivePartyDetailApi {
    @GetMapping("/party/{partyId}")
    override fun getArchivedPartyDetail(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse> =
        ApiResponse.success(getArchivedPartyDetailUseCase.invoke(partyId, principal.userId))
}
```

- Controller는 Repository나 Service를 직접 호출하지 않는다.
- DTO 변환은 UseCase 내부에서 수행한다.

### 6-2. `ArchivePartyDetailApi` (Swagger)

문서화 응답:

- 200: `ApiResponse<ArchivePartyDetailResponse>`
- 401: 인증 실패
- 403: `PARTY_FORBIDDEN`
- 404: `PARTY_NOT_FOUND`

`partyOption`, `role`은 enum value별 의미를 `@Schema`에 적는다.

### 6-3. SecurityConfig

별도 변경 없음. `/api/v1/archive/party/{partyId}`는 기본 `anyRequest().authenticated()`에 의해 인증 회원만 접근 가능. archive-list 경로(`GET /api/v1/archive`)와 base path가 같으므로, archive-list가 비로그인 200을 위해 `permitAll`을 추가한다면 detail은 그 permitAll 규칙에 매칭되지 않도록 path를 정확히 분리한다:

- archive-list: `permitAll("GET", "/api/v1/archive")` — 정확히 `/api/v1/archive`만
- detail: 별도 `permitAll` 없음 → `authenticated()` 적용

archive-list 측 spec의 permitAll matcher가 너무 넓게(`/api/v1/archive/**`) 잡혀 있다면 그 PR과 정합을 맞춰 좁힌다.

---

## 7. DTO

### 7-1. `ArchivePartyDetailResponse`

```kotlin
data class ArchivePartyDetailResponse(
    val partyId: Long,
    val partyName: String,
    val partyOption: PartyOption,
    val role: ArchiveRole,
    val partyStartedAt: LocalDateTime,
    val partyEndedAt: LocalDateTime,
    val participantCount: Long,
    val paperCount: Long,
    val participants: List<ArchiveParticipantResponse>,
    val chatMessages: List<ArchiveChatMessageResponse>,
    val chatHasMore: Boolean,
    val myPaperWritten: Boolean,
    val myPaperContent: String?,
    val myPaperWriterNickname: String?,
    val myPaperWrapperImageUrl: String?,
)

enum class ArchiveRole { HOST, PARTICIPANT }
```

### 7-2. `ArchiveParticipantResponse`

```kotlin
data class ArchiveParticipantResponse(
    val nickname: String,
)
```

### 7-3. `ArchiveChatMessageResponse`

```kotlin
data class ArchiveChatMessageResponse(
    val id: Long,
    val authorName: String,
    val content: String,
    val sentAt: LocalDateTime,
)
```

---

## 8. 시간 정책

- KST 기준 `LocalDateTime` 문자열로 직렬화 (기존 API와 일치).
- `partyEndedAt = Party.endedAt() = startedAt + Party.ENDED_AFTER_DAYS(7)`.
- 동일 요청 안에서는 `now` 값을 사용하지 않는다 (현재 시각 의존 분기 없음).

---

## 9. 테스트 계획

### 9-1. Controller 통합 테스트

새 파일: `src/test/kotlin/com/team2/server/party/api/ArchivePartyDetailControllerTest.kt`

`@SpringBootTest` + `@Import(TestcontainersConfiguration::class)`.

검증:

- REALTIME 참가자, 본인 미작성:
  - 200, `role="PARTICIPANT"`, `participants.size == participantCount`, `myPaperWritten=false`, `myPaperContent/WriterNickname/WrapperImageUrl=null`
- REALTIME 참가자, 본인 작성:
  - `myPaperWritten=true`, 3개 필드가 본인 `RollingPaper` 값과 일치
- REALTIME 주최자:
  - `role="HOST"`. 주최자도 `Participant`가 자동 생성되어 권한 통과
- PAPER_ONLY:
  - `partyOption="PAPER_ONLY"`, `participants=[]`, `participantCount=0`, `chatMessages=[]`, `chatHasMore=false`
- PAPER_ONLY 본인 작성:
  - `myPaperWritten=true` + 3개 필드 채움
- 비로그인: 401
- 다른 파티에만 속한 회원: 403 `PARTY_FORBIDDEN`
- 없는 `partyId`: 404 `PARTY_NOT_FOUND`
- 채팅 0개: `chatMessages=[]`, `chatHasMore=false`
- 채팅 60개:
  - `chatMessages.length=50`, `chatHasMore=true`, `createdAt DESC` + `id DESC` 정렬
- `paperCount`는 `RollingPaper` count by party와 일치
- `participants[].nickname`은 `RealtimeParticipantProfile.nickname` 전체와 일치, `id ASC` 정렬
- `chatMessages[].authorName`은 `RealtimeParticipantProfile.nickname` 기준

### 9-2. UseCase 단위 테스트 (선택)

- `myPaperWritten=false` 분기에서 wrapper 이미지 조회 호출이 없음(불필요 쿼리 방지)
- `chatHasMore` 경계: `chatTotal == 50`이면 `false`, `chatTotal == 51`이면 `true`

### 9-3. Security 테스트

기존 `SecurityIntegrationTest`에 케이스 추가:

- `GET /api/v1/archive/party/{partyId}`: 토큰 없이 401
- invalid Bearer token: 401

### 9-4. 전체 테스트

```bash
./gradlew test
```

`TestcontainersConfiguration` 경유 검증 후 Docker 컨테이너 누수 확인:

```bash
docker ps -a --filter "label=org.testcontainers"
```

---

## 10. 구현 순서

승인 후 진행 순서:

1. `ArchiveRole`, `ArchiveParticipantResponse`, `ArchiveChatMessageResponse`, `ArchivePartyDetailResponse` DTO 추가
2. Repository 메서드 추가
   - `ParticipantRepository.findByPartyIdAndUserId(...)` (archive-list와 중복 시 그대로 재사용)
   - `RealtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(...)`, `findAllByParticipantIdIn(...)`
   - `RollingPaperRepository.countByPartyId(...)`, `findByWriterId(...)`
   - `ChatMessageRepository.countByPartyId(...)`, `findRecentByPartyId(...)`
3. `GetArchivedPartyDetailUseCase` 추가
4. `ArchivePartyDetailApi`, `ArchivePartyDetailController` 추가
5. `ArchivePartyDetailControllerTest` 추가
6. `SecurityIntegrationTest` 케이스 추가
7. 검증 실행
   - `./gradlew test --tests com.team2.server.party.api.ArchivePartyDetailControllerTest`
   - 필요 시 `./gradlew test`

---

## 11. 미확정 / 가정

1. archive-list spec의 `ParticipantRepository.findByPartyIdAndUserId` 추가가 같은 PR에 들어오지 않으면 이번 PR에서 직접 추가한다. 시그니처 충돌은 없다.
2. 헤더 이미지는 프론트가 정적 자원으로 처리한다. 추후 동적 헤더가 필요해지면 `headerImageUrl` 필드를 별도 과제로 추가한다.
3. `Party.endedAt() = startedAt + 7일`은 현재 모델 그대로 사용한다. 화면 표시상 "M.dd - M.dd" 기간 렌더링은 프론트 책임이다.
4. `RealtimeParticipantProfile`의 닉네임은 변경 가능성이 낮다고 보고 join 방식으로 처리한다. 향후 nickname 변경 정책이 도입되면 `ChatMessage`에 `authorNameSnapshot` 컬럼 도입을 별도 과제로 다룬다.
5. 채팅을 보낸 `Participant`는 입장한 회원이므로 정상적으로 `RealtimeParticipantProfile`이 있다. 데이터 정합 문제로 profile이 없으면 `authorName`을 `""`로 직렬화하며, 별도 에러로 처리하지 않는다.
6. 보관함 list에 등장하지만 본인 `Participant`가 끊긴 비정상 케이스는 없다는 전제 (list가 `Participant` 기반이므로 일관). 만약 발생 시 detail은 `PARTY_FORBIDDEN`을 반환한다.
7. 채팅 메시지 cap은 `CHAT_RECENT_LIMIT = 50`이다. 향후 더 많은 채팅 노출이 필요하면 별도 `GET /api/v1/archive/party/{partyId}/chat` 페이지네이션 API를 분리한다.
