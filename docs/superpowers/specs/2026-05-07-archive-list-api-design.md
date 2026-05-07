# Archive List API 설계 — 보관함 리스트

- 작성일: 2026-05-07
- 대상: `com.team2.server.party` (Kotlin + Spring Boot)
- 관련 spec: [`2026-04-29-layered-architecture-design.md`](2026-04-29-layered-architecture-design.md)

---

## 0. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 보관함 항목 정의 | 사용자가 호스트로 만든 파티 + 참여한 파티 모두 |
| `type` 매핑 | `partyOption == REALTIME → "PARTY"`, `PAPER_ONLY → "PAPER"` |
| 정렬 키 | `participant.id DESC` — 사용자 관점에서 보관함에 닿은 순서(만든 시점 또는 참여한 시점이 가장 최근인 항목이 위) |
| 상태 필터 | 없음 — 시작 전/진행 중/종료 모두 포함 |
| `title` 매핑 | `party.name`, null이면 빈 문자열 `""` |
| 인증 | `UserPrincipal?` nullable. 비로그인 시 200 빈 응답 |
| cursor 형식 | 마지막 항목의 `participant.id` 문자열 |
| `id` 직렬화 | `Long → string` (Jackson `@JsonFormat(shape = STRING)`) |
| `date` 직렬화 | ISO 8601 + KST 오프셋 (`2026-05-12T22:10:00+09:00`) |
| 라우트 | `GET /api/v1/archive` |
| 패키지 | `party/` feature 내, 4-레이어 (`api/`, `application/usecase/`, `infrastructure/persistence/`) |
| `ParticipantRepository` 위치 | 이번 PR 범위 외 — 마이그레이션 브랜치(`refactor/4-layered-architecture`) 책임. 기존 위치에 메서드만 추가 |

핵심 발견: develop의 `GetUpcomingPartiesUseCase` 패턴 분석 결과, **호스트도 자기 파티에 `Participant` 레코드가 자동 생성됨**. 따라서 보관함은 `Participant` 단일 테이블 조회로 host + 참가자 모두 커버. UNION/DISTINCT 불필요.

---

## 1. API 스펙

### 1-1. 요청

```
GET /api/v1/archive?cursor={lastParticipantId}&size={1..50}
Authorization: Bearer <jwt>          # 선택. 없으면 빈 응답
```

| 파라미터 | 필수 | 타입 | 기본 | 검증 | 설명 |
|---|---|---|---|---|---|
| `cursor` | 아니오 | Long? | null | `@Min(1)` | 마지막으로 받은 항목의 `id`. null이면 첫 페이지 |
| `size` | 아니오 | Int | 20 | `@Min(1) @Max(50)` | 한 페이지 항목 수 |

### 1-2. 성공 응답 (200)

```json
{
  "status": 200,
  "data": {
    "items": [
      {
        "id": "1024",
        "type": "PARTY",
        "title": "김루카 생일 파티",
        "date": "2026-05-12T22:10:00+09:00"
      }
    ],
    "nextCursor": "1024",
    "totalCount": 37
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `items[]` | array | 항목 목록 (정렬 최신순) |
| `items[].id` | string | `participant.id` 문자열 — cursor와 동일 키 |
| `items[].type` | `"PARTY"` \| `"PAPER"` | `partyOption` 매핑 |
| `items[].title` | string (non-null) | `party.name` 또는 `""` |
| `items[].date` | string | `party.endedAt()` ISO 8601 + KST 오프셋 |
| `nextCursor` | string \| null | 다음 페이지 cursor. 없으면 null |
| `totalCount` | number | 사용자 보관함 전체 개수. 헤더 "보관함 N개" 표시용. 매 페이지 동일 |

### 1-3. 비로그인 응답 (200)

```json
{
  "status": 200,
  "data": { "items": [], "nextCursor": null, "totalCount": 0 }
}
```

### 1-4. 에러 응답

| 상황 | 코드 | ErrorCode |
|---|---|---|
| `cursor < 1` 또는 파싱 실패 | 400 | `INVALID_INPUT` |
| `size < 1` 또는 `size > 50` | 400 | `INVALID_INPUT` |

---

## 2. 패키지 구조

`party/` feature 안에 4-레이어 패키지로 신규 추가. 기존 `controller/`, `usecase/`, `repository/`는 건드리지 않음 (마이그레이션 브랜치가 통합 시 함께 이동).

```
party/
├── api/                                            ◀ 신규
│   ├── ArchiveController.kt                        @RequestMapping("/api/v1/archive")
│   ├── ArchiveApi.kt                               @Tag(name = "Archive")
│   └── dto/
│       ├── ArchiveListResponse.kt
│       ├── ArchiveListItemResponse.kt
│       └── ArchiveItemType.kt                      enum { PARTY, PAPER }
├── application/usecase/                            ◀ 신규
│   └── GetMyArchiveUseCase.kt                      @Transactional(readOnly = true)
└── repository/
    └── ParticipantRepository.kt                    ◀ 메서드 2개 추가 (기존 위치 유지)
```

`ArchiveItemType`은 enum이며, `PartyOption`과의 매핑은 DTO 변환 시점에 수행.

---

## 3. 데이터 흐름

```
ArchiveController.getArchive(principal, cursor, size)
  └─ GetMyArchiveUseCase.invoke(userId = principal?.userId, cursor, size)
        ├─ if (userId == null) return EMPTY_RESPONSE
        ├─ rows = participantRepository.findArchiveByUserId(userId, cursor, PageRequest.of(0, size + 1))
        ├─ totalCount = participantRepository.countArchiveByUserId(userId)
        ├─ hasNext = rows.size > size
        ├─ pageItems = rows.take(size)
        ├─ items = pageItems.map { ArchiveListItemResponse.from(it) }
        ├─ nextCursor = if (hasNext) pageItems.last().id.toString() else null
        └─ return ArchiveListResponse(items, nextCursor, totalCount)
```

- `size + 1` fetch 패턴: 별도 hasNext 쿼리 없이 다음 페이지 존재 여부 판정.
- `totalCount`는 별도 `COUNT(*)` 쿼리 1건. 매 페이지 동일 값이지만 FE 헤더가 페이지 새로고침마다 정확값을 요구하므로 매번 계산.
- 비로그인 분기는 UseCase에서 처리 (빈 응답이 도메인 의미를 가지므로).

---

## 4. Repository 메서드

`ParticipantRepository`에 메서드 2개 추가:

```kotlin
@Query(
    """
    SELECT p
    FROM Participant p
    JOIN FETCH p.party party
    WHERE p.user.id = :userId
      AND (:cursor IS NULL OR p.id < :cursor)
    ORDER BY p.id DESC
    """
)
fun findArchiveByUserId(
    userId: Long,
    cursor: Long?,
    pageable: Pageable,
): List<Participant>

@Query("SELECT COUNT(p) FROM Participant p WHERE p.user.id = :userId")
fun countArchiveByUserId(userId: Long): Long
```

- 정렬 `participant.id DESC` ≡ `participant.createdAt DESC` (auto-increment id). **사용자 관점의 "보관함 최신순"** — 호스트로 만들었거나 참여자로 가입한 시점이 가장 최근인 항목이 위.
- 참고: 이 정렬은 `party.endedAt() DESC`(=`party.createdAt DESC`)와 **동치가 아님**. 사용자가 옛날 파티에 최근 참여한 경우 두 정렬 결과가 갈림. 보관함 UX 의도를 "내가 닿은 순"으로 해석해 `participant.id`를 정렬 키로 채택. 파티 자체 종료 시각 기준이 필요하면 `(party.created_at, participant.id)` 복합 cursor로 전환 필요.
- `JOIN FETCH p.party`: 매핑 시 `party.partyOption`, `party.name`, `party.endedAt()` 접근으로 인한 N+1 방지.
- `Pageable = PageRequest.of(0, size + 1)`.

### 인덱스 권고

```
CREATE INDEX idx_participant_user_id_id ON participant (user_id, id DESC);
```

이번 PR에서는 도입하지 않음 — DB 마이그레이션은 별도 PR/Flyway로 검토.

---

## 5. UseCase 형태

```kotlin
@Service
class GetMyArchiveUseCase(
    private val participantRepository: ParticipantRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(userId: Long?, cursor: Long?, size: Int): ArchiveListResponse {
        if (userId == null) return EMPTY

        val rows = participantRepository.findArchiveByUserId(
            userId = userId,
            cursor = cursor,
            pageable = PageRequest.of(0, size + 1),
        )
        val hasNext = rows.size > size
        val pageItems = rows.take(size)

        return ArchiveListResponse(
            items = pageItems.map(ArchiveListItemResponse::from),
            nextCursor = if (hasNext) pageItems.last().id.toString() else null,
            totalCount = participantRepository.countArchiveByUserId(userId),
        )
    }

    companion object {
        private val EMPTY = ArchiveListResponse(items = emptyList(), nextCursor = null, totalCount = 0)
    }
}
```

spec 한도 점검:

- 클래스 ≈ 30줄 (60 이내) ✅
- 의존성 1개 (4 이내) ✅
- `@Transactional(readOnly = true)` UseCase에만 ✅
- Service 신설 없음 — 도메인 변경 없는 read-only 흐름이라 spec 2-2 매트릭스의 "조회만" 허용 범위에 해당.

---

## 6. Controller / API 형태

```kotlin
// api/ArchiveController.kt
@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(
    private val getMyArchiveUseCase: GetMyArchiveUseCase,
) : ArchiveApi {

    @GetMapping
    override fun getArchive(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) @Min(1) cursor: Long?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ApiResponse<ArchiveListResponse> =
        ApiResponse.success(getMyArchiveUseCase.invoke(principal?.userId, cursor, size))
}
```

```kotlin
// api/ArchiveApi.kt
@Tag(name = "Archive", description = "보관함 API")
interface ArchiveApi {
    @Operation(
        summary = "보관함 리스트 조회",
        description = "사용자가 호스트로 만들었거나 참여한 파티 목록을 최신순으로 조회한다. 비로그인은 빈 응답을 반환한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "보관함 조회 성공")
    @ValidationErrorResponse
    @InternalServerErrorResponse
    fun getArchive(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "마지막으로 받은 항목의 id. 첫 페이지면 생략") cursor: Long?,
        @Parameter(description = "페이지 크기. 1~50, 기본 20") size: Int,
    ): ApiResponse<ArchiveListResponse>
}
```

비로그인이 정상 흐름이므로 `@AuthErrorResponses`는 붙이지 않음.

---

## 7. DTO 정의

```kotlin
// api/dto/ArchiveListResponse.kt
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 리스트 응답")
data class ArchiveListResponse(
    @Schema(description = "보관함 항목")
    val items: List<ArchiveListItemResponse>,
    @Schema(description = "다음 페이지 cursor. 없으면 null", nullable = true, example = "1024")
    val nextCursor: String?,
    @Schema(description = "보관함 전체 개수 (헤더 표시용)", example = "37")
    val totalCount: Long,
)

// api/dto/ArchiveListItemResponse.kt
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 항목")
data class ArchiveListItemResponse(
    @Schema(description = "항목 ID (participant.id)", example = "1024")
    val id: String,
    @Schema(description = "항목 타입", allowableValues = ["PARTY", "PAPER"])
    val type: ArchiveItemType,
    @Schema(description = "파티 이름. 없으면 빈 문자열", example = "김루카 생일 파티")
    val title: String,
    @Schema(description = "파티 종료 시각 (KST)", example = "2026-05-12T22:10:00+09:00")
    val date: OffsetDateTime,
) {
    companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun from(participant: Participant): ArchiveListItemResponse {
            val party = participant.party
            return ArchiveListItemResponse(
                id = participant.id.toString(),
                type = ArchiveItemType.from(party.partyOption),
                title = party.name ?: "",
                date = party.endedAt().atOffset(KST),
            )
        }
    }
}

// api/dto/ArchiveItemType.kt
enum class ArchiveItemType {
    PARTY, PAPER;

    companion object {
        fun from(option: PartyOption): ArchiveItemType =
            when (option) {
                PartyOption.REALTIME -> PARTY
                PartyOption.PAPER_ONLY -> PAPER
            }
    }
}
```

`date`는 `OffsetDateTime`로 받아 `+09:00` 오프셋이 직렬화에 자연 포함되도록. 별도 Jackson 글로벌 설정 불필요.

---

## 8. 경계 케이스 매트릭스

| 케이스 | 입력 | 처리 | 응답 |
|---|---|---|---|
| 비로그인 | principal == null | UseCase가 EMPTY 반환 | 200, `[]` / null / 0 |
| 보관함 없음 | userId 있음, 결과 0 | 정상 흐름 | 200, `[]` / null / 0 |
| 첫 페이지 일부만 | rows ≤ size | hasNext = false | nextCursor = null |
| 첫 페이지 가득 + 다음 있음 | rows == size + 1 | hasNext = true | nextCursor = 마지막 id |
| 마지막 페이지 정확히 size | rows == size, 다음 호출에서 빈 결과 | hasNext = false | nextCursor = null |
| host + participant 중복 | 호스트도 participant 레코드 보유 | 단일 row | 자연 처리 |
| `party.name == null` | DB에 null | DTO에서 `""` 치환 | `title: ""` |
| 잘못된 cursor (`-1`, `"abc"`) | `@Min(1)` / 타입 변환 실패 | Spring 검증 → 400 | `INVALID_INPUT` |
| 잘못된 size (`0`, `51`) | `@Min(1) @Max(50)` | Spring 검증 → 400 | `INVALID_INPUT` |
| 큰 cursor (없는 id) | `WHERE p.id < cursor` | 빈 결과 | 200, `[]` / null / totalCount |

---

## 9. 테스트 계획

`src/test/kotlin/com/team2/server/party/api/ArchiveControllerTest.kt` (신규)

develop 패턴(`MePartyControllerTest`, `RollingPaperListControllerTest`) 따름:
- `@SpringBootTest` + `MockMvc` + Testcontainers
- `DatabaseCleanup` 활용
- 실제 JPA 쓰기로 픽스처 구성

| # | 시나리오 | 검증 |
|---|---|---|
| T1 | 비로그인 호출 | 200 / `[]` / null / 0 |
| T2 | 보관함 비어 있음 | 200 / `[]` / null / 0 |
| T3 | host 1건 + participant 2건 (3건 모두) | items.size = 3, 정렬 id DESC |
| T4 | REALTIME 파티 | type = `"PARTY"` |
| T5 | PAPER_ONLY 파티 | type = `"PAPER"` |
| T6 | `party.name == null` | title = `""` |
| T7 | size=2 + 항목 3건, 첫 호출 | items.size = 2, nextCursor != null |
| T8 | T7의 nextCursor로 두 번째 호출 | items.size = 1, nextCursor = null |
| T9 | size=2 + 항목 정확 2건 | items.size = 2, nextCursor = null |
| T10 | totalCount는 페이지마다 동일 | T7/T8 totalCount == 3 |
| T11 | size=0 / size=51 | 400 `INVALID_INPUT` |
| T12 | cursor=0 / cursor=-1 / cursor="abc" | 400 `INVALID_INPUT` |
| T13 | cursor가 큰 값 (없는 id) | 200 / `[]` / null / totalCount > 0 |
| T14 | date 직렬화 형식 | `+09:00` 포함 ISO 8601 매칭 |

UseCase 단위 테스트는 별도로 두지 않음 (Controller 통합 테스트가 흐름 전체를 커버하고, UseCase 자체에 도메인 분기가 거의 없음).

---

## 10. 구현 영향 범위

| 파일 | 변경 |
|---|---|
| `party/api/ArchiveController.kt` | 신규 |
| `party/api/ArchiveApi.kt` | 신규 |
| `party/api/dto/ArchiveListResponse.kt` | 신규 |
| `party/api/dto/ArchiveListItemResponse.kt` | 신규 |
| `party/api/dto/ArchiveItemType.kt` | 신규 |
| `party/application/usecase/GetMyArchiveUseCase.kt` | 신규 |
| `party/repository/ParticipantRepository.kt` | 메서드 2개 추가 |
| `test/.../party/api/ArchiveControllerTest.kt` | 신규 |
| Flyway 마이그레이션 | 이번 PR 범위 외 (인덱스 권고만 남김) |

---

## 11. PR 분리

단일 PR. 이번 보관함 리스트 API 구현이 한 기능 단위에 맞아떨어지므로 분리하지 않음. 인덱스 추가는 별도 인프라 PR로 분리.

---

## 12. 비고 / 향후 확장 포인트

- 향후 PAPER 도메인까지 합치는 보관함(예: 받은 롤링페이퍼 단위)이 추가되면 `archive/` feature를 별도 분리하고, `ArchiveItemType`을 source 다양화하는 방향으로 확장.
- `totalCount` 계산이 무거워지면 캐시 또는 첫 페이지 응답에만 포함하는 정책으로 최적화 가능 (현재는 단일 사용자 단일 테이블이라 부담 적음).
- `participant.id` cursor의 단조 감소 가정이 깨지는 시나리오(예: 데이터 마이그레이션으로 id 재배치)에서는 `(party_created_at, id)` 복합 cursor로 전환.
