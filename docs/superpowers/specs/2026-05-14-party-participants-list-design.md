# 파티 참여자 목록 조회 API 설계

- 작성일: 2026-05-14
- 작성자: taegyu.choi
- 관련 화면: 파티 진행 기본화면 (파티 시작 전, 참가자 1명 이상)
- 대상 파티: `RealtimeParty` 전용

## 1. 배경 / 목표

실시간 파티 시작 전에 입장한 참가자를 모아 보여주는 "파티 진행 기본화면"을 그리기 위한 서버 API. 클라이언트는 응답을 받아 다음을 수행한다.

- 입장 순서대로 1~13번 캐릭터 위치 고정 배치
- 주최자 화면: 본인 외 가장 먼저 입장한 참가자를 상단 큰 캐릭터로 배치
- 참가자 화면: 본인을 상단 큰 캐릭터로 배치
- 주최자(모자쓴 캐릭터)는 하단 고정

서버는 정렬된 참가자 목록과 식별 정보를 반환하고, UI 배치는 클라이언트가 결정한다.

## 2. 비범위 (YAGNI)

- 음악 버튼, 채팅 바텀시트, 입장 안내멘트 등 UI 위젯
- WebSocket / 실시간 입장 알림 (별도 PR — 채팅 도메인)
- 페이지네이션 (최대 14명)
- 응답 캐싱 (필요시 측정 후 추가)
- PaperOnly 파티의 참가자 화면 (요구사항 없음)

## 3. API 사양

### Endpoint

```
GET /api/v1/parties/{partyId}/participants
Authorization: Bearer <jwt>      # 로그인 사용자
X-Participant-Token: <token>     # 비로그인 참가자
```

- 로그인 사용자는 `Authorization: Bearer <jwt>` 헤더를, 비로그인 참가자는 `X-Participant-Token: <participantToken>` 헤더를 사용한다. 둘 중 하나는 반드시 포함해야 한다.
- 두 헤더가 모두 있으면 `Authorization` 헤더 우선.

### Path Variable

| 이름 | 타입 | 설명 |
|---|---|---|
| partyId | Long | 파티 ID |

### Response (200 OK)

```json
{
  "status": 200,
  "data": {
    "totalCount": 4,
    "maxCount": 14,
    "participants": [
      {
        "participantId": 17,
        "joinOrder": 1,
        "nickname": "주최자닉",
        "characterId": 3,
        "characterImageUrl": "https://cdn.example.com/characters/char3.png",
        "isOwner": true,
        "isCelebrant": true,
        "isMe": false
      },
      {
        "participantId": 18,
        "joinOrder": 2,
        "nickname": "참가자A",
        "characterId": 7,
        "characterImageUrl": "https://cdn.example.com/characters/char7.png",
        "isOwner": false,
        "isCelebrant": false,
        "isMe": true
      }
    ]
  }
}
```

### 응답 필드 정의

| 필드 | 타입 | 비고 |
|---|---|---|
| totalCount | Int | 현재 참가자 수 |
| maxCount | Int | 상수 14 (`RealtimeParty.MAX_PARTICIPANTS`) |
| participants | List | `joinOrder` 오름차순 |
| participants[].participantId | Long | 식별자 |
| participants[].joinOrder | Int | 1..N, 서버가 `participant.id ASC` 기준으로 부여 |
| participants[].nickname | String | `RealtimeParticipantProfile.nickname` |
| participants[].characterId | Long? | `Character.id`. 익명 참가자는 character 없을 수 있음 → null |
| participants[].characterImageUrl | String? | sortOrder=0 이미지 (`ImageRepository` 배치 조회) |
| participants[].isOwner | Boolean | `participant.user?.id == party.ownerId` |
| participants[].isCelebrant | Boolean | `participant.isCelebrant` |
| participants[].isMe | Boolean | `participant.id == 호출자의 participantId` (JWT 호출자는 partyId+userId 조회, 토큰 호출자는 토큰으로 식별된 participant) |

> `isOwner` / `isCelebrant` 는 현재 항상 동일하지만 향후 "남이 대신 만든 파티" 시나리오에 대비해 분리. 클라이언트 측 UI 룰(모자 표시 vs 주인공 표시)을 독립적으로 매핑할 수 있음.

### Error Response

| 상황 | HTTP | code | message |
|---|---|---|---|
| JWT/토큰 모두 없음 | 401 | UNAUTHORIZED | 로그인이 필요합니다 |
| 잘못된 토큰 / 다른 파티의 토큰 | 403 | PARTY_FORBIDDEN | 파티에 대한 권한이 없습니다 |
| 참가자 아닌 사용자 | 403 | PARTY_FORBIDDEN | 파티에 대한 권한이 없습니다 |
| 존재하지 않는 partyId | 403 | PARTY_FORBIDDEN | 파티에 대한 권한이 없습니다 |
| 참가자가 호출한 PaperOnly 파티 | 400 | **PARTY_NOT_REALTIME** *(신규)* | 실시간 파티가 아닙니다 |

> 인가 → 파티 타입 검사 순서: 비참가자가 PaperOnly 파티 / 존재하지 않는 파티를 호출하면 파티 타입(400) / 존재 여부(404)가 아닌 인가 실패(403)로 응답한다. 비참가자에게 리소스 속성·존재 여부를 노출하지 않기 위함.

## 4. 레이어드 아키텍처 매핑

```
ParticipantController              (api)
        │ invoke
GetPartyParticipantsUseCase        (application/usecase, @Transactional(readOnly = true))
        │
        ├─ ParticipantService.requireCallerParticipantId(partyId, userId?, participantToken?) → Long  (인가 먼저)
        ├─ PartyService.requireRealtimeParty(partyId)        → RealtimeParty           (인가 후 파티 타입 검사)
        ├─ ParticipantService.findOrderedProfiles(partyId)   → List<RealtimeParticipantProfile>
        └─ ImageRepository.findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(CHARACTER, ids)
```

### 의존 방향 준수

- Controller → UseCase 만 호출
- UseCase → 같은 feature 의 Service 조합 (`PartyService`, `ParticipantService`)
- Service → Service 호출 없음 (UseCase 가 조합)
- UseCase 가 응답 DTO 변환 책임

## 5. 신규 / 수정 파일

### 신규

| 경로 | 역할 |
|---|---|
| `party/api/ParticipantApi.kt` | Swagger interface |
| `party/api/ParticipantController.kt` | `GET /api/v1/parties/{partyId}/participants` |
| `party/api/dto/PartyParticipantsResponse.kt` | 응답 envelope (`totalCount`, `maxCount`, `participants`) |
| `party/api/dto/PartyParticipantResponse.kt` | 개별 참가자 응답 항목 |
| `party/application/dto/PartyParticipantsResult.kt` | 응답용 application DTO (envelope) |
| `party/application/dto/PartyParticipantResult.kt` | 응답용 application DTO (item) |
| `party/application/usecase/GetPartyParticipantsUseCase.kt` | 흐름 제어, @Transactional(readOnly) |

### 수정

| 경로 | 변경 |
|---|---|
| `common/exception/ErrorCode.kt` | `PARTY_NOT_REALTIME(BAD_REQUEST, "실시간 파티가 아닙니다")` 추가 |
| `party/domain/entity/RealtimeParty.kt` | `const val MAX_PARTICIPANTS = 14` 추가 |
| `party/application/service/PartyService.kt` | `requireRealtimeParty(partyId): RealtimeParty` 추가 (status 무관) |
| `party/application/service/ParticipantService.kt` | `requireCallerParticipantId(partyId, userId?, participantToken?): Long` 추가 / `findOrderedProfiles(partyId): List<RealtimeParticipantProfile>` 추가 |
| `auth/config/SecurityConfig.kt` | `GET /api/v1/parties/*/participants` → `permitAll` (토큰 인증을 UseCase 내부에서 검증하므로 필터 우회) |
| `party/infrastructure/persistence/RealtimeParticipantProfileRepository.kt` | `findAllByPartyIdOrderByParticipantIdAsc(partyId)` JPQL JOIN FETCH 쿼리 추가 |

### Repository 쿼리

```kotlin
@Query(
    """
    SELECT rpp
    FROM RealtimeParticipantProfile rpp
    JOIN FETCH rpp.participant participant
    LEFT JOIN FETCH participant.user
    LEFT JOIN FETCH rpp.character
    WHERE participant.party.id = :partyId
    ORDER BY participant.id ASC
    """,
)
fun findAllByPartyIdOrderByParticipantIdAsc(partyId: Long): List<RealtimeParticipantProfile>
```

- 한 번의 쿼리로 N+1 회피
- `character` LEFT JOIN — 익명 참가자 (현재 코드상 character null 가능)
- `user` LEFT JOIN — 익명 참가자 처리

> 캐릭터 이미지 URL 은 `ImageRepository.findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(CHARACTER, characterIds)` 로 별도 배치 조회 후 매핑 (기존 `GetCharactersUseCase` 패턴 동일).

### Service 새 메서드

**`PartyService.requireRealtimeParty`**

```kotlin
fun requireRealtimeParty(partyId: Long): RealtimeParty {
    val party = findParty(partyId)
    if (party.partyOption != PartyOption.REALTIME) {
        throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
    }
    return Hibernate.unproxy(party) as RealtimeParty
}
```

- 기존 `findActiveRealtimeParty` 와 다름: LIVE_OPEN 상태 강제하지 않음 (시작 전 화면용)
- `findParty` private → 재사용

**`ParticipantService.requireCallerParticipantId`**

```kotlin
fun requireCallerParticipantId(
    partyId: Long,
    userId: Long?,
    participantToken: String?,
): Long {
    if (userId != null) {
        val participant = participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        return participant.id
    }
    if (participantToken != null) {
        val profile = realtimeParticipantProfileRepository.findByParticipantToken(participantToken)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        if (profile.participant.party.id != partyId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        return profile.participant.id
    }
    throw BusinessException(ErrorCode.UNAUTHORIZED)
}
```

- JWT 호출자: `partyId + userId` 로 participant 조회 → 없으면 `403 PARTY_FORBIDDEN`
- 토큰 호출자: 토큰으로 profile 조회 후 partyId 일치 검사 → 불일치/없음이면 `403 PARTY_FORBIDDEN`
- 둘 다 없으면 `401 UNAUTHORIZED`

**`ParticipantService.findOrderedProfiles`**

```kotlin
fun findOrderedProfiles(partyId: Long): List<RealtimeParticipantProfile> =
    realtimeParticipantProfileRepository.findAllByPartyIdOrderByParticipantIdAsc(partyId)
```

> `ParticipantService` 는 현재 `RealtimeParticipantProfileRepository` 를 보유하지 않음 → 의존성 5개 이내 제약 확인 필요. 현재 의존성 1개라 여유 충분.

## 6. UseCase 의사 코드

```kotlin
@Service
class GetPartyParticipantsUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): PartyParticipantsResult {
        // 인가 먼저 — 비참가자에게 파티 타입(400)을 노출하지 않기 위해
        val callerParticipantId = participantService.requireCallerParticipantId(partyId, userId, participantToken)
        val party = partyService.requireRealtimeParty(partyId)

        val profiles = participantService.findOrderedProfiles(partyId)
        val characterIds = profiles.mapNotNull { it.character?.id }.distinct()
        val imageUrlByCharacterId =
            if (characterIds.isEmpty()) emptyMap()
            else imageRepository
                .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(ImageTargetType.CHARACTER, characterIds)
                .filter { it.sortOrder == CHARACTER_IMAGE_SORT_ORDER }
                .associate { it.targetId to it.imageUrl }

        val items = profiles.mapIndexed { index, profile ->
            val participant = profile.participant
            PartyParticipantResult(
                participantId = participant.id,
                joinOrder = index + 1,
                nickname = profile.nickname,
                characterId = profile.character?.id,
                characterImageUrl = profile.character?.id?.let { imageUrlByCharacterId[it] },
                isOwner = participant.user?.id == party.ownerId,
                isCelebrant = participant.isCelebrant,
                isMe = participant.id == callerParticipantId,
            )
        }
        return PartyParticipantsResult(
            totalCount = items.size,
            maxCount = RealtimeParty.MAX_PARTICIPANTS,
            participants = items,
        )
    }

    private companion object {
        private const val CHARACTER_IMAGE_SORT_ORDER = 0
    }
}
```

- 60줄 이내, 의존성 3개 — `layered-architecture.md` 제약 만족.

## 7. 테스트 전략

`docs/testing-rules.md` 준수: `@SpringBootTest` / `@DataJpaTest` 는 반드시 `TestcontainersConfiguration` 경유.

### Controller 통합 테스트 (`@SpringBootTest`)

| 케이스 | 기대 |
|---|---|
| 주최자 호출 (참가자 4명) | 200, `isOwner=true` 1건, 응답 순서 `joinOrder` 1..4 |
| 일반 참가자 호출 | 200, 본인 `isMe=true` 1건만 |
| `X-Participant-Token` 호출 (비로그인 참가자) | 200, 토큰 owner의 `isMe=true` |
| 다른 파티의 `X-Participant-Token` 호출 | 403, PARTY_FORBIDDEN |
| 참가자 1명만 (주최자 단독) | 200, totalCount=1 |
| 참가자 14명 (가득) | 200, totalCount=14, maxCount=14 |
| 비참가자 호출 (JWT) | 403, PARTY_FORBIDDEN |
| 비참가자가 PaperOnly 파티 호출 (JWT) | 403, PARTY_FORBIDDEN (인가 우선) |
| 참가자가 PaperOnly 파티 호출 (JWT) | 400, PARTY_NOT_REALTIME |
| 존재하지 않는 partyId | 403, PARTY_FORBIDDEN (존재 노출 방지) |
| 헤더 둘 다 없음 | 401, UNAUTHORIZED |

### Repository 테스트 (`@DataJpaTest` + Testcontainers)

- `findAllByPartyIdOrderByParticipantIdAsc`: 순서 검증
- N+1 확인: Hibernate Statistics 로 쿼리 수 ≤ 2 (메인 + 이미지 배치)

### UseCase 단위 테스트

- 권한 분기 (mock service): 비참가자 → PARTY_FORBIDDEN
- 옵션 분기 (mock service): PaperOnly → PARTY_NOT_REALTIME
- 캐릭터 없는 익명 참가자: `characterImageUrl=null`

## 8. 마이그레이션 / 운영

- DB 스키마 변경 없음
- Flyway 마이그레이션 없음
- 새 ErrorCode 만 enum 추가 → 백워드 호환

## 9. 검토 체크리스트 (구현 전)

- [ ] `ParticipantService` 의존성 5개 이내 (현재 1개 → 2개로 증가, 여유 충분)
- [ ] `PartyService` 의존성 4개 이내? (`PartyService` 는 Service 라 의존성 4개 이내) — 현재 8개로 이미 초과 상태이므로 본 PR 에서는 메서드만 추가, 의존성 추가 없음
- [ ] UseCase 60줄·의존성 5개 이내
- [ ] 모든 통합 테스트 `TestcontainersConfiguration` 사용
- [ ] N+1 없음

> **주의**: `PartyService` 의존성이 이미 8개로 layered-architecture 규칙(4개 이내) 초과 상태. 본 PR 에서는 메서드 추가만 수행하고 의존성은 추가하지 않음. 의존성 정리는 별도 refactor PR 로 분리.
