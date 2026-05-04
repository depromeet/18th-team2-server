# 파티 삭제 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 파티 주최자가 파티 시작 전에 파티와 연관 데이터를 하드 딜리트하는 `DELETE /api/v1/parties/{partyId}` API 구현

**Architecture:** Service 레이어에서 외래키 의존성 역순으로 RealtimeParticipantProfile → Participant → PartyInvite → Party 순서로 직접 삭제한다. 기존 `PartyController` / `PartyService` / `PartyApi`에 기능을 추가하는 방식으로 확장한다.

**Tech Stack:** Kotlin, Spring Boot, JPA (Spring Data JPA), JUnit 5, Mockito

---

## 파일 맵

| 파일 | 변경 |
|------|------|
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | `PARTY_ALREADY_STARTED` 추가 |
| `src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt` | `findAllByPartyId` 추가 |
| `src/main/kotlin/com/team2/server/party/repository/PartyInviteRepository.kt` | `deleteAllByPartyId` 추가 |
| `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt` | `deleteAllByParticipantIdIn` 추가 |
| `src/main/kotlin/com/team2/server/party/service/PartyService.kt` | `deleteParty` 메서드 추가, repository 의존성 추가 |
| `src/main/kotlin/com/team2/server/party/controller/PartyApi.kt` | delete 엔드포인트 선언 추가 |
| `src/main/kotlin/com/team2/server/party/controller/PartyController.kt` | delete 엔드포인트 구현 추가 |
| `src/test/kotlin/com/team2/server/party/service/PartyServiceTest.kt` | deleteParty 단위 테스트 추가 |
| `src/test/kotlin/com/team2/server/party/controller/PartyControllerTest.kt` | DELETE 통합 테스트 추가 |

---

## Task 1: ErrorCode에 PARTY_ALREADY_STARTED 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`

- [ ] **Step 1: ErrorCode에 신규 코드 추가**

`PARTY_ENDED` 아래에 추가한다.

```kotlin
PARTY_ENDED(HttpStatus.BAD_REQUEST, "이미 종료된 파티입니다"),
PARTY_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 파티는 삭제할 수 없습니다."),
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt
git commit -m "feat: PARTY_ALREADY_STARTED 에러 코드 추가"
```

---

## Task 2: Repository에 삭제용 메서드 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/party/repository/PartyInviteRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt`

- [ ] **Step 1: ParticipantRepository에 findAllByPartyId 추가**

```kotlin
interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByPartyAndUser(party: Party, user: User): Participant?
    fun existsByPartyAndUser(party: Party, user: User): Boolean
    fun existsByPartyIdAndUserId(partyId: Long, userId: Long): Boolean
    fun findAllByPartyId(partyId: Long): List<Participant>
}
```

- [ ] **Step 2: PartyInviteRepository에 deleteAllByPartyId 추가**

```kotlin
interface PartyInviteRepository : JpaRepository<PartyInvite, Long> {
    fun findByToken(token: String): PartyInvite?
    fun findByPartyIdAndExpiresAtAfter(partyId: Long, now: LocalDateTime): PartyInvite?
    fun deleteAllByPartyId(partyId: Long)
}
```

- [ ] **Step 3: RealtimeParticipantProfileRepository에 deleteAllByParticipantIdIn 추가**

```kotlin
interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt
git add src/main/kotlin/com/team2/server/party/repository/PartyInviteRepository.kt
git add src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt
git commit -m "feat: 파티 삭제용 Repository 메서드 추가"
```

---

## Task 3: PartyService에 deleteParty 단위 테스트 작성

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/service/PartyServiceTest.kt`

기존 테스트 클래스에 Mock 필드와 테스트 케이스를 추가한다.

- [ ] **Step 1: PartyServiceTest에 Mock 필드 및 헬퍼 추가**

기존 `PartyServiceTest` 클래스에 아래 필드를 추가한다.

```kotlin
@Mock
lateinit var partyInviteRepository: PartyInviteRepository
```

`import com.team2.server.party.repository.PartyInviteRepository`도 추가한다.

- [ ] **Step 2: 파티 없을 때 PARTY_NOT_FOUND 테스트 작성**

기존 테스트 클래스 내 `// --- 파티 삭제 ---` 섹션을 추가하고 아래 테스트를 작성한다.

```kotlin
// --- 파티 삭제 ---

@Test
fun `deleteParty 파티가 없으면 PARTY_NOT_FOUND`() {
    whenever(partyRepository.findById(99L)).thenReturn(Optional.empty())

    val ex = assertThrows<BusinessException> {
        partyService.deleteParty(partyId = 99L, userId = 1L)
    }

    assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    verify(partyRepository, never()).deleteById(any())
}
```

- [ ] **Step 3: 주최자가 아닐 때 PARTY_FORBIDDEN 테스트 작성**

```kotlin
@Test
fun `deleteParty 주최자가 아니면 PARTY_FORBIDDEN`() {
    val party = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().plusDays(1),
    )
    setId(party, 10L)
    whenever(partyRepository.findById(10L)).thenReturn(Optional.of(party))

    val ex = assertThrows<BusinessException> {
        partyService.deleteParty(partyId = 10L, userId = 999L)
    }

    assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    verify(partyRepository, never()).deleteById(any())
}
```

- [ ] **Step 4: 파티 시작 후 삭제 시 PARTY_ALREADY_STARTED 테스트 작성**

```kotlin
@Test
fun `deleteParty 파티가 이미 시작됐으면 PARTY_ALREADY_STARTED`() {
    val party = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().minusMinutes(1),
    )
    setId(party, 10L)
    whenever(partyRepository.findById(10L)).thenReturn(Optional.of(party))

    val ex = assertThrows<BusinessException> {
        partyService.deleteParty(partyId = 10L, userId = 1L)
    }

    assertEquals(ErrorCode.PARTY_ALREADY_STARTED, ex.errorCode)
    verify(partyRepository, never()).deleteById(any())
}
```

- [ ] **Step 5: 정상 삭제 — 연관 데이터 삭제 순서 검증 테스트 작성**

```kotlin
@Test
fun `deleteParty 정상 삭제 시 연관 데이터를 순서대로 삭제한다`() {
    val party = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().plusDays(1),
    )
    setId(party, 10L)

    val participant1 = Participant(party = party, user = null, isCelebrant = true)
    setId(participant1, 100L)
    val participant2 = Participant(party = party, user = null, isCelebrant = false)
    setId(participant2, 101L)

    whenever(partyRepository.findById(10L)).thenReturn(Optional.of(party))
    whenever(participantRepository.findAllByPartyId(10L))
        .thenReturn(listOf(participant1, participant2))

    partyService.deleteParty(partyId = 10L, userId = 1L)

    val order = inOrder(
        realtimeParticipantProfileRepository,
        participantRepository,
        partyInviteRepository,
        partyRepository,
    )
    order.verify(realtimeParticipantProfileRepository)
        .deleteAllByParticipantIdIn(listOf(100L, 101L))
    order.verify(participantRepository).deleteAll(listOf(participant1, participant2))
    order.verify(partyInviteRepository).deleteAllByPartyId(10L)
    order.verify(partyRepository).deleteById(10L)
}
```

`import org.mockito.kotlin.inOrder`도 추가한다.

- [ ] **Step 6: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.party.service.PartyServiceTest" 2>&1 | tail -20
```

Expected: `deleteParty` 관련 테스트 4개 FAIL (메서드 미존재)

---

## Task 4: PartyService에 deleteParty 구현

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/service/PartyService.kt`

- [ ] **Step 1: PartyService에 partyInviteRepository 의존성 추가**

생성자 파라미터에 추가한다.

```kotlin
@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val userRepository: UserRepository,
)
```

`import com.team2.server.party.repository.PartyInviteRepository`도 추가한다.

- [ ] **Step 2: deleteParty 메서드 구현**

`createParty` 아래에 추가한다.

```kotlin
@Transactional
fun deleteParty(
    partyId: Long,
    userId: Long,
) {
    val party = partyRepository.findById(partyId)
        .orElseThrow { BusinessException(ErrorCode.PARTY_NOT_FOUND) }

    if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

    if (!LocalDateTime.now().isBefore(party.startedAt)) {
        throw BusinessException(ErrorCode.PARTY_ALREADY_STARTED)
    }

    val participants = participantRepository.findAllByPartyId(partyId)
    val participantIds = participants.map { it.id }

    realtimeParticipantProfileRepository.deleteAllByParticipantIdIn(participantIds)
    participantRepository.deleteAll(participants)
    partyInviteRepository.deleteAllByPartyId(partyId)
    partyRepository.deleteById(partyId)
}
```

`import java.time.LocalDateTime`이 없으면 추가한다.

- [ ] **Step 3: 단위 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.team2.server.party.service.PartyServiceTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 모든 테스트 GREEN

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/service/PartyService.kt
git add src/test/kotlin/com/team2/server/party/service/PartyServiceTest.kt
git commit -m "feat: 파티 삭제 서비스 로직 구현"
```

---

## Task 5: PartyApi 인터페이스 및 PartyController에 DELETE 엔드포인트 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/controller/PartyApi.kt`
- Modify: `src/main/kotlin/com/team2/server/party/controller/PartyController.kt`

- [ ] **Step 1: PartyApi에 deleteParty 선언 추가**

기존 `createParty` 선언 아래에 추가한다.

```kotlin
@Operation(
    summary = "파티 삭제",
    security = [SecurityRequirement(name = "Bearer Authentication")],
)
@SwaggerApiResponse(responseCode = "204", description = "파티 삭제 성공")
@AuthErrorResponses
@InternalServerErrorResponse
fun deleteParty(
    @Parameter(hidden = true) principal: UserPrincipal,
    @Parameter(description = "파티 ID", example = "1") partyId: Long,
): ApiResponse<Unit>
```

- [ ] **Step 2: PartyController에 deleteParty 구현 추가**

```kotlin
@ResponseStatus(HttpStatus.NO_CONTENT)
@DeleteMapping("/{partyId}")
override fun deleteParty(
    @AuthenticationPrincipal principal: UserPrincipal,
    @PathVariable partyId: Long,
): ApiResponse<Unit> {
    partyService.deleteParty(partyId = partyId, userId = principal.userId)
    return ApiResponse.success(HttpStatus.NO_CONTENT, Unit)
}
```

`import org.springframework.web.bind.annotation.DeleteMapping`도 추가한다.

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

---

## Task 6: PartyControllerTest에 통합 테스트 추가

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/controller/PartyControllerTest.kt`

- [ ] **Step 1: 인증 없이 DELETE 시 401 테스트 작성**

기존 테스트 클래스 내 마지막 테스트 아래에 추가한다.

```kotlin
@Test
fun `인증 없이 파티 삭제 시 401`() {
    mockMvc
        .delete("/api/v1/parties/1")
        .andExpect {
            status { isUnauthorized() }
        }
}
```

`import org.springframework.test.web.servlet.delete`도 추가한다.

- [ ] **Step 2: 존재하지 않는 파티 삭제 시 실패 테스트 작성**

```kotlin
@Test
fun `존재하지 않는 파티 삭제 시 404`() {
    val token = tokenProvider.issue(saveUser("kakao-del-notfound", "del-notfound@kakao.local"))

    mockMvc
        .delete("/api/v1/parties/99999") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
        }
}
```

- [ ] **Step 3: 주최자가 아닌 유저가 삭제 시도 시 403 테스트 작성**

```kotlin
@Test
fun `주최자가 아닌 유저가 삭제 시 403`() {
    val ownerToken = tokenProvider.issue(saveUser("kakao-del-owner", "del-owner@kakao.local"))
    val otherToken = tokenProvider.issue(saveUser("kakao-del-other", "del-other@kakao.local"))

    val partyId = createParty(ownerToken, "2099-12-31", "14:30")

    mockMvc
        .delete("/api/v1/parties/$partyId") {
            header("Authorization", "Bearer $otherToken")
        }.andExpect {
            status { isForbidden() }
        }
}
```

- [ ] **Step 4: 이미 시작된 파티 삭제 시 409 테스트 작성**

```kotlin
@Test
fun `이미 시작된 파티 삭제 시 409`() {
    val token = tokenProvider.issue(saveUser("kakao-del-started", "del-started@kakao.local"))
    val partyId = createParty(token, "2000-01-01", "00:00")

    mockMvc
        .delete("/api/v1/parties/$partyId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isConflict() }
        }
}
```

- [ ] **Step 5: 정상 삭제 성공 — 204 및 DB 데이터 삭제 확인 테스트 작성**

```kotlin
@Test
fun `파티 시작 전 주최자가 삭제 시 204`() {
    val token = tokenProvider.issue(saveUser("kakao-del-success", "del-success@kakao.local"))
    val partyId = createParty(token, "2099-12-31", "14:30")

    mockMvc
        .delete("/api/v1/parties/$partyId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

    assertEquals(false, partyRepository.existsById(partyId))
    assertEquals(0, participantRepository.findAllByPartyId(partyId).size)
}
```

- [ ] **Step 6: createParty 헬퍼 메서드 추가**

테스트 클래스 내 `saveUser` 아래에 추가한다.

```kotlin
private fun createParty(
    token: String,
    date: String,
    time: String,
): Long {
    val result = mockMvc
        .post("/api/v1/parties/REALTIME") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"celebrantNickname": "홍길동", "startedDate": "$date", "startTime": "$time"}"""
            header("Authorization", "Bearer $token")
        }.andReturn()

    val body = result.response.contentAsString
    val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)
    return node["data"]["partyId"].asLong()
}
```

- [ ] **Step 7: 통합 테스트 실행 — 모두 통과 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.PartyControllerTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 모든 테스트 GREEN

- [ ] **Step 8: 전체 테스트 실행**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/controller/PartyApi.kt
git add src/main/kotlin/com/team2/server/party/controller/PartyController.kt
git add src/test/kotlin/com/team2/server/party/controller/PartyControllerTest.kt
git commit -m "feat: 파티 삭제 API 엔드포인트 구현"
```
